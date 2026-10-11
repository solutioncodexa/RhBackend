package ma.rhflow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.rhflow.domain.entity.Attendance;
import ma.rhflow.domain.entity.AuditLog;
import ma.rhflow.domain.entity.Contract;
import ma.rhflow.domain.entity.Department;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.LeaveBalance;
import ma.rhflow.domain.entity.LeaveRequest;
import ma.rhflow.domain.entity.PayrollPeriod;
import ma.rhflow.domain.entity.Payslip;
import ma.rhflow.domain.enums.PermissionCodes;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.domain.repository.AttendanceRepository;
import ma.rhflow.domain.repository.AuditLogRepository;
import ma.rhflow.domain.repository.ContractRepository;
import ma.rhflow.domain.repository.DepartmentRepository;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.domain.repository.EmployeeRequestRepository;
import ma.rhflow.domain.repository.LeaveBalanceRepository;
import ma.rhflow.domain.repository.LeaveRequestRepository;
import ma.rhflow.domain.repository.PayrollPeriodRepository;
import ma.rhflow.domain.repository.PayslipRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.util.JsonMaps;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRequestRepository employeeRequestRepository;
    private final ContractRepository contractRepository;
    private final DepartmentRepository departmentRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository;
    private final AuditLogRepository auditLogRepository;
    private final PermissionService permissionService;
    private final ma.rhflow.domain.repository.HrDocumentRepository hrDocumentRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UserPrincipal actor) {
        Map<String, Object> m = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        List<Employee> employees = visibleEmployees(actor);
        Set<Long> ids = employees.stream().map(Employee::getId).collect(Collectors.toSet());
        String scope = actor.getRole() == UserRole.EMPLOYEE ? "self" : actor.getRole() == UserRole.MANAGER ? "team" : "company";
        long total = employees.size();
        long active = employees.stream().filter(e -> e.getStatus() == null || "ACTIVE".equals(e.getStatus())).count();
        List<Attendance> attendance = attendanceRepository.findAll().stream()
                .filter(a -> a.getEmployeeId() != null && ids.contains(a.getEmployeeId()))
                .toList();
        long onLeave = attendance.stream().filter(a -> today.equals(a.getDate()) && isLeave(a.getStatus())).count();
        long absent = attendance.stream().filter(a -> today.equals(a.getDate()) && "ABSENT".equals(a.getStatus())).count();
        LocalDate from = today.withDayOfMonth(1);
        List<Attendance> monthAtt = attendance.stream()
                .filter(a -> a.getDate() != null && !a.getDate().isBefore(from) && !a.getDate().isAfter(today))
                .toList();
        long presentish = monthAtt.stream().filter(a -> isPresentish(a.getStatus())).count();
        BigDecimal rate = monthAtt.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(presentish * 100.0 / monthAtt.size()).setScale(1, RoundingMode.HALF_UP);
        List<LeaveRequest> leaves = leaveRequestRepository.findAll().stream()
                .filter(r -> r.getEmployeeId() != null && ids.contains(r.getEmployeeId()))
                .toList();
        long pendingLeaves = leaves.stream().filter(r -> "PENDING".equalsIgnoreCase(r.getStatus())).count();
        long pendingReq = employeeRequestRepository.findAll().stream()
                .filter(r -> "PENDING".equals(r.getStatus()))
                .filter(r -> "company".equals(scope) || (r.getEmployeeId() != null && ids.contains(r.getEmployeeId())))
                .count();
        LocalDate horizon = today.plusDays(90);
        Map<Long, String> names = employees.stream().collect(Collectors.toMap(Employee::getId, this::fullName, (a, b) -> a));
        List<Map<String, Object>> expiring = new ArrayList<>();
        List<Contract> contracts = contractRepository.findAll().stream()
                .filter(c -> c.getEmployeeId() != null && ids.contains(c.getEmployeeId()))
                .toList();
        for (Contract c : contracts) {
            if (c.getEndDate() != null && !c.getEndDate().isAfter(horizon)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", c.getId());
                row.put("endDate", c.getEndDate());
                row.put("employeeName", names.getOrDefault(c.getEmployeeId(), ""));
                row.put("type", c.getType());
                String alert = "90";
                if (c.getEndDate().isBefore(today)) {
                    alert = "OVERDUE";
                } else if (!c.getEndDate().isAfter(today.plusDays(7))) {
                    alert = "7";
                } else if (!c.getEndDate().isAfter(today.plusDays(30))) {
                    alert = "30";
                }
                row.put("alert", alert);
                expiring.add(row);
            }
        }
        m.put("scope", scope);
        m.put("totalEmployees", total);
        m.put("headcount", total);
        m.put("employees", total);
        m.put("activeEmployees", active);
        m.put("active", active);
        m.put("onLeaveToday", onLeave);
        m.put("leaveToday", onLeave);
        m.put("absentToday", absent);
        m.put("absencesToday", absent);
        m.put("attendanceRate", rate);
        m.put("pendingLeaves", pendingLeaves);
        m.put("leavePending", pendingLeaves);
        m.put("pendingRequests", pendingReq);
        m.put("requestsPending", pendingReq);
        m.put("expiringContracts", expiring.size());
        m.put("contractsExpiring", expiring.size());
        m.put("expiringContractList", expiring);
        boolean payroll = permissionService.has(actor, PermissionCodes.PAYROLL_VIEW);
        List<Payslip> slips = payslipRepository.findAll().stream()
                .filter(p -> p.getEmployeeId() != null && (payroll ? ids.contains(p.getEmployeeId()) : actor.getEmployeeId() != null && actor.getEmployeeId().equals(p.getEmployeeId())))
                .toList();
        if (payroll) {
            PayrollPeriod current = payrollPeriodRepository
                    .findByYearAndMonth(today.getYear(), today.getMonthValue())
                    .orElse(null);
            m.put("employerCost", current == null ? BigDecimal.ZERO : current.getEmployerCost());
            m.put("currentPayrollPeriod", current == null ? null : current.getMonth() + "/" + current.getYear());
            m.put("payrollMonth", current == null ? null : current.getMonth() + "/" + current.getYear());
        }
        if (actor.getEmployeeId() != null) {
            leaveBalanceRepository.findAll().stream()
                    .filter(b -> actor.getEmployeeId().equals(b.getEmployeeId()) && b.getYear() != null && b.getYear() == today.getYear())
                    .map(LeaveBalance::getRemaining)
                    .reduce(BigDecimal::add)
                    .ifPresent(v -> {
                        m.put("leaveBalance", v);
                        m.put("remainingLeave", v);
                    });
            slips.stream()
                    .filter(p -> actor.getEmployeeId().equals(p.getEmployeeId()))
                    .max((a, b) -> {
                        int y = Integer.compare(a.getYear() == null ? 0 : a.getYear(), b.getYear() == null ? 0 : b.getYear());
                        return y != 0 ? y : Integer.compare(a.getMonth() == null ? 0 : a.getMonth(), b.getMonth() == null ? 0 : b.getMonth());
                    })
                    .map(Payslip::getNet)
                    .ifPresent(n -> m.put("lastNet", n));
        }
        m.put("charts", charts(employees, attendance, contracts, leaves, slips, today, payroll));
        List<Map<String, Object>> activity = new ArrayList<>();
        for (AuditLog a : auditLogRepository.findAll(PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "createdAt")))) {
            if (!payroll && isPayrollEntity(a.getEntityType())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("message", activityLabel(a));
            row.put("action", a.getAction());
            row.put("entityType", a.getEntityType());
            row.put("createdAt", a.getCreatedAt());
            activity.add(row);
            if (activity.size() == 8) {
                break;
            }
        }
        m.put("recentActivity", activity);
        m.put("activity", activity);
        return m;
    }

    private Map<String, Object> charts(List<Employee> employees, List<Attendance> attendance, List<Contract> contracts,
            List<LeaveRequest> leaves, List<Payslip> slips, LocalDate today, boolean payroll) {
        Map<String, Object> charts = new LinkedHashMap<>();
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int w = 7; w >= 0; w--) {
            LocalDate end = today.minusDays(w * 7L);
            LocalDate start = end.minusDays(6);
            List<Attendance> rows = attendance.stream()
                    .filter(a -> a.getDate() != null && !a.getDate().isBefore(start) && !a.getDate().isAfter(end))
                    .toList();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", "S" + (8 - w));
            point.put("present", rows.stream().filter(a -> "PRESENT".equals(a.getStatus()) || "REMOTE".equals(a.getStatus()) || "MISSION".equals(a.getStatus())).count());
            point.put("late", rows.stream().filter(a -> "LATE".equals(a.getStatus())).count());
            point.put("absent", rows.stream().filter(a -> "ABSENT".equals(a.getStatus())).count());
            point.put("leave", rows.stream().filter(a -> isLeave(a.getStatus())).count());
            trend.add(point);
        }
        charts.put("attendanceTrend", trend);
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MM/yy");
        List<Map<String, Object>> headcount = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate monthEnd = today.minusMonths(i).withDayOfMonth(today.minusMonths(i).lengthOfMonth());
            long n = employees.stream().filter(e -> e.getHireDate() == null || !e.getHireDate().isAfter(monthEnd)).count();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", monthEnd.format(monthFmt));
            point.put("value", n);
            headcount.add(point);
        }
        charts.put("headcountTrend", headcount);
        LocalDate from = today.withDayOfMonth(1);
        List<Attendance> month = attendance.stream()
                .filter(a -> a.getDate() != null && !a.getDate().isBefore(from) && !a.getDate().isAfter(today))
                .toList();
        charts.put("attendanceMix", List.of(
                slice("Présents", month.stream().filter(a -> "PRESENT".equals(a.getStatus()) || "REMOTE".equals(a.getStatus()) || "MISSION".equals(a.getStatus())).count()),
                slice("Retards", month.stream().filter(a -> "LATE".equals(a.getStatus())).count()),
                slice("Absents", month.stream().filter(a -> "ABSENT".equals(a.getStatus())).count()),
                slice("Congés", month.stream().filter(a -> isLeave(a.getStatus())).count())
        ));
        Map<Long, String> departments = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(Department::getId, d -> d.getName() == null ? "Département" : d.getName(), (a, b) -> a));
        Map<String, Long> byDept = employees.stream().collect(Collectors.groupingBy(
                e -> e.getDepartmentId() == null ? "Sans département" : departments.getOrDefault(e.getDepartmentId(), "Sans département"),
                LinkedHashMap::new,
                Collectors.counting()));
        charts.put("departments", byDept.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> slice(e.getKey(), e.getValue()))
                .toList());
        Map<String, Long> byType = contracts.stream()
                .filter(c -> c.getStatus() == null || "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .collect(Collectors.groupingBy(c -> c.getType() == null ? "Autre" : c.getType(), LinkedHashMap::new, Collectors.counting()));
        charts.put("contracts", byType.entrySet().stream().map(e -> slice(e.getKey(), e.getValue())).toList());
        charts.put("leaves", List.of(
                slice("En attente", leaves.stream().filter(r -> "PENDING".equalsIgnoreCase(r.getStatus())).count()),
                slice("Acceptés", leaves.stream().filter(r -> "APPROVED".equalsIgnoreCase(r.getStatus())).count()),
                slice("Refusés", leaves.stream().filter(r -> "REJECTED".equalsIgnoreCase(r.getStatus()) || "REFUSED".equalsIgnoreCase(r.getStatus())).count())
        ));
        if (payroll) {
            List<Payslip> monthSlips = slips.stream()
                    .filter(p -> p.getYear() != null && p.getMonth() != null && p.getYear() == today.getYear() && p.getMonth() == today.getMonthValue())
                    .toList();
            charts.put("charges", List.of(
                    slice("CNSS", sum(monthSlips, Payslip::getEmployerCnss)),
                    slice("AMO", sum(monthSlips, Payslip::getEmployerAmo)),
                    slice("Allocations", sum(monthSlips, Payslip::getEmployerAf)),
                    slice("PSCT", sum(monthSlips, Payslip::getEmployerPsct)),
                    slice("PSLT", sum(monthSlips, Payslip::getEmployerPslt)),
                    slice("TFP", sum(monthSlips, Payslip::getEmployerTfp))
            ));
        }
        return charts;
    }

    private List<Employee> visibleEmployees(UserPrincipal actor) {
        List<Employee> all = employeeRepository.findAll();
        if (actor.getRole() == UserRole.EMPLOYEE && actor.getEmployeeId() != null) {
            return all.stream().filter(e -> actor.getEmployeeId().equals(e.getId())).toList();
        }
        if (actor.getRole() == UserRole.MANAGER && actor.getEmployeeId() != null) {
            Long self = actor.getEmployeeId();
            List<Employee> team = all.stream().filter(e -> self.equals(e.getManagerId())).toList();
            return team.isEmpty() ? all.stream().filter(e -> self.equals(e.getId())).toList() : team;
        }
        return all;
    }

    private Map<String, Object> slice(String label, long value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("value", value);
        return row;
    }

    private Map<String, Object> slice(String label, BigDecimal value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("value", value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP));
        return row;
    }

    private BigDecimal sum(List<Payslip> slips, java.util.function.Function<Payslip, BigDecimal> getter) {
        return slips.stream().map(getter).map(v -> v == null ? BigDecimal.ZERO : v).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isPresentish(String status) {
        return status != null && List.of("PRESENT", "REMOTE", "MISSION", "LATE").contains(status);
    }

    private boolean isLeave(String status) {
        return "ON_LEAVE".equals(status) || "LEAVE".equals(status);
    }

    private boolean isPayrollEntity(String entity) {
        if (entity == null) {
            return false;
        }
        String e = entity.toLowerCase(Locale.ROOT);
        return e.contains("payslip") || e.contains("payroll") || e.contains("bulletin");
    }

    private String activityLabel(AuditLog log) {
        String action = switch (log.getAction() == null ? "" : log.getAction().toUpperCase(Locale.ROOT)) {
            case "CREATE" -> "Création";
            case "UPDATE" -> "Modification";
            case "DELETE" -> "Suppression";
            case "APPROVE" -> "Validation";
            case "REJECT" -> "Refus";
            case "UPLOAD" -> "Dépôt";
            default -> log.getAction() == null ? "Événement" : log.getAction();
        };
        String entity = switch (log.getEntityType() == null ? "" : log.getEntityType()) {
            case "employees" -> "employé";
            case "documents" -> "document";
            case "contracts" -> "contrat";
            case "leave-request", "leave-requests" -> "congé";
            case "leave-balances" -> "solde de congé";
            case "payslips" -> "bulletin";
            case "announcements" -> "annonce";
            default -> log.getEntityType() == null ? "" : log.getEntityType();
        };
        return entity.isBlank() ? action : action + " · " + entity;
    }

    private String fullName(Employee e) {
        return ((e.getFirstName() == null ? "" : e.getFirstName()) + " " + (e.getLastName() == null ? "" : e.getLastName())).trim();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(String q, UserPrincipal actor) {
        if (q == null || q.length() < 2) {
            return List.of();
        }
        String n = q.toLowerCase();
        List<Map<String, Object>> hits = new ArrayList<>();
        if (permissionService.has(actor, PermissionCodes.EMPLOYEES_VIEW) || actor.getRole() == UserRole.ADMIN) {
            for (Employee e : employeeRepository.findAll()) {
                String blob = (e.getFirstName() + " " + e.getLastName() + " " + e.getEmail() + " " + e.getCin()).toLowerCase();
                if (blob.contains(n)) {
                    Map<String, Object> h = JsonMaps.employeeRef(e);
                    h.put("type", "employee");
                    h.put("href", "/app/employees");
                    hits.add(h);
                }
            }
        }
        for (Contract c : contractRepository.findAll()) {
            if (String.valueOf(c.getType()).toLowerCase().contains(n)) {
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("id", c.getId());
                h.put("name", c.getType());
                h.put("type", "contract");
                h.put("href", "/app/contracts");
                hits.add(h);
            }
        }
        if (permissionService.has(actor, PermissionCodes.DOCUMENTS_VIEW) || actor.getRole() == UserRole.ADMIN) {
            for (ma.rhflow.domain.entity.HrDocument d : hrDocumentRepository.findAll()) {
                String blob = String.valueOf(d.getName()).toLowerCase();
                if (blob.contains(n)) {
                    Map<String, Object> h = new LinkedHashMap<>();
                    h.put("id", d.getId());
                    h.put("name", d.getName());
                    h.put("type", "document");
                    h.put("href", "/app/documents");
                    hits.add(h);
                }
            }
        }
        for (ma.rhflow.domain.entity.Department d : departmentRepository.findAll()) {
            if (String.valueOf(d.getName()).toLowerCase().contains(n)) {
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("id", d.getId());
                h.put("name", d.getName());
                h.put("type", "department");
                h.put("href", "/app/departments");
                hits.add(h);
            }
        }
        return hits;
    }
}
