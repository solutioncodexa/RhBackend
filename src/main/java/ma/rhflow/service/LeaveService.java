package ma.rhflow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.Attendance;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.Holiday;
import ma.rhflow.domain.entity.LeaveBalance;
import ma.rhflow.domain.entity.LeaveRequest;
import ma.rhflow.domain.entity.LeaveType;
import ma.rhflow.domain.entity.WorkingHours;
import ma.rhflow.domain.enums.PermissionCodes;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.domain.repository.AttendanceRepository;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.domain.repository.HolidayRepository;
import ma.rhflow.domain.repository.LeaveBalanceRepository;
import ma.rhflow.domain.repository.LeaveRequestRepository;
import ma.rhflow.domain.repository.LeaveTypeRepository;
import ma.rhflow.domain.repository.WorkingHoursRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.tenant.TenantContext;
import ma.rhflow.util.JsonMaps;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final HolidayRepository holidayRepository;
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final ScopeService scopeService;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final WhatsAppService whatsAppService;
    private final PdfDocumentService pdfDocumentService;

    public Page<Map<String, Object>> list(Map<String, String> filters, int page, int size, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.LEAVE_VIEW);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "id"));
        Set<Long> visible = scopeService.visibleEmployeeIds(actor);
        Specification<LeaveRequest> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (visible != null) {
                p.add(root.get("employeeId").in(visible));
            }
            if (StringUtils.hasText(filters.get("status"))) {
                p.add(cb.equal(root.get("status"), filters.get("status")));
            }
            if (StringUtils.hasText(filters.get("from"))) {
                p.add(cb.greaterThanOrEqualTo(root.get("endDate"), LocalDate.parse(filters.get("from").substring(0, 10))));
            }
            if (StringUtils.hasText(filters.get("to"))) {
                p.add(cb.lessThanOrEqualTo(root.get("startDate"), LocalDate.parse(filters.get("to").substring(0, 10))));
            }
            if (StringUtils.hasText(filters.get("employeeId"))) {
                Long employeeId = Long.valueOf(filters.get("employeeId"));
                scopeService.assertEmployeeVisible(actor, employeeId);
                p.add(cb.equal(root.get("employeeId"), employeeId));
            }
            if (StringUtils.hasText(filters.get("leaveTypeId"))) {
                p.add(cb.equal(root.get("leaveTypeId"), Long.valueOf(filters.get("leaveTypeId"))));
            }
            String q = filters.get("q");
            if (StringUtils.hasText(q)) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                List<Long> ids = employeeRepository.findAll((er, eq, ecb) -> ecb.or(
                        ecb.like(ecb.lower(er.get("firstName")), like),
                        ecb.like(ecb.lower(er.get("lastName")), like),
                        ecb.like(ecb.lower(ecb.coalesce(er.get("email"), "")), like)
                )).stream().map(Employee::getId).toList();
                if (ids.isEmpty()) {
                    return cb.disjunction();
                }
                p.add(root.get("employeeId").in(ids));
            }
            return p.isEmpty() ? cb.conjunction() : cb.and(p.toArray(Predicate[]::new));
        };
        return leaveRequestRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.LEAVE_REQUEST);
        Long employeeId = JsonMaps.lng(body, "employeeId");
        if (employeeId == null) {
            employeeId = actor.getEmployeeId();
        }
        if (employeeId == null) {
            throw BusinessException.badRequest("Employé requis");
        }
        if (actor.getRole() == UserRole.EMPLOYEE && actor.getEmployeeId() != null
                && !actor.getEmployeeId().equals(employeeId)) {
            throw BusinessException.forbidden("Vous ne pouvez poser un congé que pour vous-même");
        }
        if (actor.getRole() == UserRole.MANAGER) {
            scopeService.assertEmployeeVisible(actor, employeeId);
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
        Long typeId = JsonMaps.lng(body, "leaveTypeId");
        LeaveType type = leaveTypeRepository.findById(typeId)
                .orElseThrow(() -> BusinessException.badRequest("Type de congé invalide"));
        LocalDate start = JsonMaps.date(body, "startDate");
        LocalDate end = JsonMaps.date(body, "endDate");
        if (start == null || end == null || end.isBefore(start)) {
            throw BusinessException.badRequest("Dates invalides");
        }
        assertNoOverlap(employeeId, start, end, null);
        BigDecimal days = BigDecimal.valueOf(workingDays(employee, start, end));
        if (days.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.badRequest("Aucun jour ouvrable sur cette période");
        }
        LeaveRequest r = new LeaveRequest();
        r.setTenantId(TenantContext.requireTenantId());
        r.setEmployeeId(employeeId);
        r.setLeaveTypeId(type.getId());
        r.setStartDate(start);
        r.setEndDate(end);
        r.setDays(days);
        r.setStatus("PENDING");
        r.setComment(JsonMaps.str(body, "comment"));
        leaveRequestRepository.save(r);
        auditService.log(actor, "CREATE", "leave-request", r.getId(), "pending");
        notificationService.notifyManagerOf(employeeId, "Congé en attente",
                employee.getFirstName() + " " + employee.getLastName() + " : " + start + " → " + end,
                "/app/leave-requests");
        return toDto(r);
    }

    @Transactional
    public Map<String, Object> decide(Long id, String action, Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.LEAVE_APPROVE);
        LeaveRequest r = leaveRequestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Demande introuvable"));
        if (!"PENDING".equals(r.getStatus()) && !"MANAGER_APPROVED".equals(r.getStatus())) {
            throw BusinessException.badRequest("Demande déjà traitée");
        }
        if (!scopeService.canApproveLeave(actor, r.getEmployeeId())) {
            throw BusinessException.forbidden("Seul le manager direct (ou un admin) peut décider");
        }
        String comment = body == null ? null : JsonMaps.str(body, "comment");
        r.setDecisionComment(comment);
        r.setDecidedBy(actor.getId());
        boolean admin = actor.getRole() == ma.rhflow.domain.enums.UserRole.ADMIN
                || actor.getRole() == ma.rhflow.domain.enums.UserRole.SUPER_ADMIN;
        if ("approve".equals(action)) {
            if (!admin && "PENDING".equals(r.getStatus())) {
                r.setStatus("MANAGER_APPROVED");
                r.setApprovalStep(1);
                leaveRequestRepository.save(r);
                notificationService.notifyAdmins("Congé à valider RH", "Demande " + r.getId() + " validée par le manager.",
                        "/app/leave-requests");
                return toDto(r);
            }
            if (!admin) {
                throw BusinessException.forbidden("Seul un administrateur approuve définitivement le congé");
            }
            r.setStatus("APPROVED");
            r.setApprovalStep(2);
            LeaveType type = leaveTypeRepository.findById(r.getLeaveTypeId()).orElse(null);
            if (type != null && type.isDeductsAnnual()) {
                applyBalance(r, type, false);
            }
            markAttendanceOnLeave(r);
            notificationService.notifyEmployee(r.getEmployeeId(), "Congé approuvé",
                    "Votre congé du " + r.getStartDate() + " au " + r.getEndDate() + " est approuvé.",
                    "/portal/leave");
            notificationService.notifyAdmins("Congé approuvé", "Demande " + r.getId() + " approuvée.",
                    "/app/leave-requests");
            mailDecision(employeeRepository.findById(r.getEmployeeId()).orElse(null), true, r);
        } else {
            if (!admin && !"PENDING".equals(r.getStatus())) {
                throw BusinessException.forbidden("Seul un administrateur peut refuser un congé déjà validé");
            }
            r.setStatus("REJECTED");
            notificationService.notifyEmployee(r.getEmployeeId(), "Congé refusé",
                    "Votre congé du " + r.getStartDate() + " au " + r.getEndDate() + " a été refusé.",
                    "/portal/leave");
            mailDecision(employeeRepository.findById(r.getEmployeeId()).orElse(null), false, r);
        }
        leaveRequestRepository.save(r);
        auditService.log(actor, action.toUpperCase(Locale.ROOT), "leave-request", r.getId(), comment);
        return toDto(r);
    }

    @Transactional
    public Map<String, Object> cancel(Long id, UserPrincipal actor) {
        LeaveRequest r = leaveRequestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Demande introuvable"));
        boolean own = actor.getEmployeeId() != null && actor.getEmployeeId().equals(r.getEmployeeId());
        boolean hr = actor.getRole() == UserRole.ADMIN || permissionService.has(actor, PermissionCodes.LEAVE_APPROVE);
        if (!own && !hr) {
            throw BusinessException.forbidden("Annulation non autorisée");
        }
        if ("CANCELLED".equals(r.getStatus()) || "REJECTED".equals(r.getStatus())) {
            throw BusinessException.badRequest("Demande déjà clôturée");
        }
        if ("APPROVED".equals(r.getStatus())) {
            LeaveType type = leaveTypeRepository.findById(r.getLeaveTypeId()).orElse(null);
            if (type != null && type.isDeductsAnnual()) {
                applyBalance(r, type, true);
            }
            clearAttendanceOnLeave(r);
        }
        r.setStatus("CANCELLED");
        leaveRequestRepository.save(r);
        auditService.log(actor, "CANCEL", "leave-request", r.getId(), null);
        notificationService.notifyEmployee(r.getEmployeeId(), "Congé annulé",
                "Le congé du " + r.getStartDate() + " au " + r.getEndDate() + " a été annulé.",
                "/portal/leave");
        return toDto(r);
    }

    public int workingDays(LocalDate start, LocalDate end) {
        return workingDays(null, start, end);
    }

    public int workingDays(Employee employee, LocalDate start, LocalDate end) {
        Set<LocalDate> holidays = holidayRepository.findByDateBetween(start, end).stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());
        return WorkCalendar.workingDays(start, end, workDaysOf(employee), holidays);
    }

    public BigDecimal accruedAnnual(Employee employee, int year) {
        LocalDate hire = employee.getHireDate() == null ? LocalDate.of(year, 1, 1) : employee.getHireDate();
        if (hire.getYear() > year) {
            return BigDecimal.ZERO;
        }
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate from = hire.isAfter(yearStart) ? hire : yearStart;
        LocalDate to = LocalDate.of(year, 12, 31);
        int months = (to.getYear() - from.getYear()) * 12 + (to.getMonthValue() - from.getMonthValue()) + 1;
        months = Math.min(12, Math.max(0, months));
        BigDecimal base = BigDecimal.valueOf(1.5).multiply(BigDecimal.valueOf(months));
        int seniorityYears = Period.between(hire, LocalDate.of(year, 12, 31)).getYears();
        int extraBlocks = seniorityYears / 5;
        BigDecimal extra = BigDecimal.valueOf(1.5).multiply(BigDecimal.valueOf(extraBlocks));
        return base.add(extra).min(BigDecimal.valueOf(30)).setScale(1, RoundingMode.HALF_UP);
    }

    private void applyBalance(LeaveRequest r, LeaveType type, boolean credit) {
        Employee employee = employeeRepository.findById(r.getEmployeeId()).orElse(null);
        int year = r.getStartDate().getYear();
        LeaveBalance bal = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(r.getEmployeeId(), type.getId(), year)
                .orElseGet(() -> {
                    LeaveBalance b = new LeaveBalance();
                    b.setTenantId(r.getTenantId());
                    b.setEmployeeId(r.getEmployeeId());
                    b.setLeaveTypeId(type.getId());
                    b.setYear(year);
                    b.setAccrued(employee == null ? BigDecimal.valueOf(18) : accruedAnnual(employee, year));
                    b.setUsed(BigDecimal.ZERO);
                    b.setRemaining(b.getAccrued());
                    return b;
                });
        if (nz(bal.getAccrued()).compareTo(BigDecimal.ZERO) == 0 && employee != null) {
            bal.setAccrued(accruedAnnual(employee, year));
        }
        if (credit) {
            bal.setUsed(nz(bal.getUsed()).subtract(r.getDays()).max(BigDecimal.ZERO));
        } else {
            BigDecimal remaining = nz(bal.getAccrued()).subtract(nz(bal.getUsed()));
            if (remaining.compareTo(r.getDays()) < 0) {
                throw BusinessException.badRequest("Solde insuffisant (" + remaining + " jours)");
            }
            bal.setUsed(nz(bal.getUsed()).add(r.getDays()));
        }
        bal.setRemaining(nz(bal.getAccrued()).subtract(nz(bal.getUsed())));
        leaveBalanceRepository.save(bal);
    }

    private void assertNoOverlap(Long employeeId, LocalDate start, LocalDate end, Long exceptId) {
        List<LeaveRequest> existing = leaveRequestRepository.findByEmployeeIdAndStatusIn(employeeId, List.of("PENDING", "APPROVED"));
        for (LeaveRequest other : existing) {
            if (exceptId != null && exceptId.equals(other.getId())) {
                continue;
            }
            if (!other.getEndDate().isBefore(start) && !other.getStartDate().isAfter(end)) {
                throw BusinessException.conflict("Une demande existe déjà sur cette période");
            }
        }
    }

    private Set<DayOfWeek> workDaysOf(Employee employee) {
        List<WorkingHours> all = workingHoursRepository.findAll();
        WorkingHours match = null;
        if (employee != null) {
            match = all.stream().filter(w -> employee.getId().equals(w.getEmployeeId())).findFirst().orElse(null);
            if (match == null && employee.getDepartmentId() != null) {
                match = all.stream().filter(w -> employee.getDepartmentId().equals(w.getDepartmentId()) && w.getEmployeeId() == null)
                        .findFirst().orElse(null);
            }
        }
        if (match == null) {
            match = all.stream().filter(w -> w.getEmployeeId() == null && w.getDepartmentId() == null).findFirst().orElse(null);
        }
        if (match == null || !StringUtils.hasText(match.getWorkDays())) {
            return EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        String raw = match.getWorkDays().toUpperCase(Locale.ROOT);
        Map<String, DayOfWeek> map = new java.util.HashMap<>();
        map.put("MON", DayOfWeek.MONDAY);
        map.put("TUE", DayOfWeek.TUESDAY);
        map.put("WED", DayOfWeek.WEDNESDAY);
        map.put("THU", DayOfWeek.THURSDAY);
        map.put("FRI", DayOfWeek.FRIDAY);
        map.put("SAT", DayOfWeek.SATURDAY);
        map.put("SUN", DayOfWeek.SUNDAY);
        map.put("LUN", DayOfWeek.MONDAY);
        map.put("MAR", DayOfWeek.TUESDAY);
        map.put("MER", DayOfWeek.WEDNESDAY);
        map.put("JEU", DayOfWeek.THURSDAY);
        map.put("VEN", DayOfWeek.FRIDAY);
        map.put("SAM", DayOfWeek.SATURDAY);
        map.put("DIM", DayOfWeek.SUNDAY);
        if (raw.contains("MON-FRI") || raw.contains("LUN-VEN")) {
            days.addAll(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        }
        for (String token : raw.split("[,;|/\\s]+")) {
            DayOfWeek d = map.get(token);
            if (d != null) {
                days.add(d);
            }
            if (token.contains("-") && token.length() >= 7) {
                String[] parts = token.split("-");
                if (parts.length == 2 && map.get(parts[0]) != null && map.get(parts[1]) != null) {
                    DayOfWeek a = map.get(parts[0]);
                    DayOfWeek b = map.get(parts[1]);
                    for (DayOfWeek x = a; ; x = x.plus(1)) {
                        days.add(x);
                        if (x == b) {
                            break;
                        }
                    }
                }
            }
        }
        if (raw.contains("1") || raw.contains("LUNDI")) days.add(DayOfWeek.MONDAY);
        if (days.isEmpty()) {
            return EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        return days;
    }

    private void markAttendanceOnLeave(LeaveRequest r) {
        Employee employee = employeeRepository.findById(r.getEmployeeId()).orElse(null);
        Set<DayOfWeek> work = workDaysOf(employee);
        Set<LocalDate> holidays = holidayRepository.findByDateBetween(r.getStartDate(), r.getEndDate()).stream()
                .map(Holiday::getDate).collect(Collectors.toSet());
        for (LocalDate d = r.getStartDate(); !d.isAfter(r.getEndDate()); d = d.plusDays(1)) {
            if (!work.contains(d.getDayOfWeek()) || holidays.contains(d)) {
                continue;
            }
            LocalDate day = d;
            attendanceRepository.findByEmployeeIdAndDate(r.getEmployeeId(), day).ifPresentOrElse(a -> {
                a.setStatus("ON_LEAVE");
                attendanceRepository.save(a);
            }, () -> {
                Attendance a = new Attendance();
                a.setTenantId(r.getTenantId());
                a.setEmployeeId(r.getEmployeeId());
                a.setDate(day);
                a.setStatus("ON_LEAVE");
                attendanceRepository.save(a);
            });
        }
    }

    private void clearAttendanceOnLeave(LeaveRequest r) {
        for (LocalDate d = r.getStartDate(); !d.isAfter(r.getEndDate()); d = d.plusDays(1)) {
            LocalDate day = d;
            attendanceRepository.findByEmployeeIdAndDate(r.getEmployeeId(), day).ifPresent(a -> {
                if ("ON_LEAVE".equals(a.getStatus())) {
                    attendanceRepository.delete(a);
                }
            });
        }
    }

    private void mailDecision(Employee employee, boolean approved, LeaveRequest r) {
        if (employee == null) {
            return;
        }
        String subject = approved ? "RHFlow — congé approuvé" : "RHFlow — congé refusé";
        String body = "Bonjour " + employee.getFirstName() + ",\nVotre demande du "
                + r.getStartDate() + " au " + r.getEndDate() + " a été "
                + (approved ? "approuvée" : "refusée") + ".";
        mailService.send(employee.getEmail(), subject, body, "/portal/leave", "Voir mes congés");
        whatsAppService.notify(employee.getPhone(), subject);
    }

    public byte[] authorizationPdf(Long id, UserPrincipal actor) {
        LeaveRequest r = leaveRequestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Demande introuvable"));
        if (!"APPROVED".equals(r.getStatus())) {
            throw BusinessException.badRequest("Le PDF est disponible une fois le congé approuvé");
        }
        boolean own = actor.getEmployeeId() != null && actor.getEmployeeId().equals(r.getEmployeeId());
        if (!own) {
            permissionService.require(actor, PermissionCodes.LEAVE_VIEW);
            scopeService.assertEmployeeVisible(actor, r.getEmployeeId());
        }
        Employee employee = employeeRepository.findById(r.getEmployeeId())
                .orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
        String extra = "Congé du " + r.getStartDate() + " au " + r.getEndDate()
                + (r.getDays() == null ? "" : " (" + r.getDays().stripTrailingZeros().toPlainString() + " jours)");
        return pdfDocumentService.attestation(employee, "AUTORISATION_CONGE", extra);
    }

    public Map<String, Object> toDto(LeaveRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("employeeId", r.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(employeeRepository.findById(r.getEmployeeId()).orElse(null)));
        m.put("leaveTypeId", r.getLeaveTypeId());
        LeaveType t = r.getLeaveTypeId() == null ? null : leaveTypeRepository.findById(r.getLeaveTypeId()).orElse(null);
        m.put("leaveType", JsonMaps.leaveTypeRef(t));
        m.put("type", t == null ? null : t.getName());
        m.put("startDate", r.getStartDate());
        m.put("endDate", r.getEndDate());
        m.put("days", r.getDays());
        m.put("workingDays", r.getDays());
        m.put("status", r.getStatus());
        m.put("approvalStep", r.getApprovalStep());
        m.put("comment", r.getComment());
        m.put("decisionComment", r.getDecisionComment());
        return m;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
