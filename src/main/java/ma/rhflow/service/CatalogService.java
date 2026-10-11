package ma.rhflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import ma.rhflow.domain.entity.Announcement;
import ma.rhflow.domain.entity.Attendance;
import ma.rhflow.domain.entity.AuditLog;
import ma.rhflow.domain.entity.BusinessTrip;
import ma.rhflow.domain.entity.Candidate;
import ma.rhflow.domain.entity.Contract;
import ma.rhflow.domain.entity.Department;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.EmployeeRequest;
import ma.rhflow.domain.entity.Expense;
import ma.rhflow.domain.entity.Holiday;
import ma.rhflow.domain.entity.HrDocument;
import ma.rhflow.domain.entity.JobOffer;
import ma.rhflow.domain.entity.LeaveBalance;
import ma.rhflow.domain.entity.LeaveType;
import ma.rhflow.domain.entity.Notification;
import ma.rhflow.domain.entity.Objective;
import ma.rhflow.domain.entity.OffboardingTask;
import ma.rhflow.domain.entity.OnboardingTask;
import ma.rhflow.domain.entity.Payslip;
import ma.rhflow.domain.entity.PerformanceReview;
import ma.rhflow.domain.entity.Position;
import ma.rhflow.domain.entity.WorkingHours;
import ma.rhflow.domain.enums.PermissionCodes;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.domain.repository.AnnouncementRepository;
import ma.rhflow.domain.repository.AttendanceRepository;
import ma.rhflow.domain.repository.AuditLogRepository;
import ma.rhflow.domain.repository.BusinessTripRepository;
import ma.rhflow.domain.repository.CandidateRepository;
import ma.rhflow.domain.repository.ContractRepository;
import ma.rhflow.domain.repository.DepartmentRepository;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.domain.repository.EmployeeRequestRepository;
import ma.rhflow.domain.repository.ExpenseRepository;
import ma.rhflow.domain.repository.HolidayRepository;
import ma.rhflow.domain.repository.HrDocumentRepository;
import ma.rhflow.domain.repository.JobOfferRepository;
import ma.rhflow.domain.repository.LeaveBalanceRepository;
import ma.rhflow.domain.repository.LeaveTypeRepository;
import ma.rhflow.domain.repository.NotificationRepository;
import ma.rhflow.domain.repository.ObjectiveRepository;
import ma.rhflow.domain.repository.OffboardingTaskRepository;
import ma.rhflow.domain.repository.OnboardingTaskRepository;
import ma.rhflow.domain.repository.PayslipRepository;
import ma.rhflow.domain.repository.PerformanceReviewRepository;
import ma.rhflow.domain.repository.PositionRepository;
import ma.rhflow.domain.repository.WorkingHoursRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.service.FileStorageService.StoredFile;
import ma.rhflow.tenant.TenantContext;
import ma.rhflow.util.JsonMaps;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final ContractRepository contractRepository;
    private final AttendanceRepository attendanceRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final HrDocumentRepository hrDocumentRepository;
    private final EmployeeRequestRepository employeeRequestRepository;
    private final AnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
    private final ExpenseRepository expenseRepository;
    private final BusinessTripRepository businessTripRepository;
    private final PerformanceReviewRepository performanceReviewRepository;
    private final ObjectiveRepository objectiveRepository;
    private final JobOfferRepository jobOfferRepository;
    private final CandidateRepository candidateRepository;
    private final OnboardingTaskRepository onboardingTaskRepository;
    private final OffboardingTaskRepository offboardingTaskRepository;
    private final HolidayRepository holidayRepository;
    private final AuditLogRepository auditLogRepository;
    private final PayslipRepository payslipRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final FileStorageService fileStorageService;
    private final ScopeService scopeService;
    private final QuotaService quotaService;
    private final NotificationService notificationService;
    private final PdfDocumentService pdfDocumentService;

    public Page<Map<String, Object>> list(String resource, Map<String, String> filters, int page, int size, UserPrincipal actor) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "id"));
        String q = filters.getOrDefault("q", "");
        Long own = actor.getEmployeeId();
        boolean emp = actor.getRole() == UserRole.EMPLOYEE;
        Set<Long> visible = scopeService.visibleEmployeeIds(actor);
        return switch (resource) {
            case "employees" -> {
                permissionService.require(actor, PermissionCodes.EMPLOYEES_VIEW);
                Specification<Employee> spec = (root, query, cb) -> {
                    List<Predicate> p = new ArrayList<>();
                    if (StringUtils.hasText(q)) {
                        String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                        p.add(cb.or(
                                cb.like(cb.lower(root.get("firstName")), like),
                                cb.like(cb.lower(root.get("lastName")), like),
                                cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like),
                                cb.like(cb.lower(cb.coalesce(root.get("cin"), "")), like)
                        ));
                    }
                    if (StringUtils.hasText(filters.get("status"))) {
                        p.add(cb.equal(root.get("status"), filters.get("status")));
                    }
                    if (StringUtils.hasText(filters.get("city"))) {
                        String like = "%" + filters.get("city").toLowerCase(Locale.ROOT) + "%";
                        p.add(cb.like(cb.lower(cb.coalesce(root.get("city"), "")), like));
                    }
                    Long departmentId = parseLong(filters.get("departmentId"));
                    if (departmentId != null) {
                        p.add(cb.equal(root.get("departmentId"), departmentId));
                    }
                    if (emp && own != null) {
                        p.add(cb.equal(root.get("id"), own));
                    } else if (visible != null) {
                        p.add(root.get("id").in(visible));
                    }
                    return cb.and(p.toArray(Predicate[]::new));
                };
                yield employeeRepository.findAll(spec, pageable).map(this::toEmployee);
            }
            case "departments" -> {
                permissionService.requireAny(actor, PermissionCodes.EMPLOYEES_VIEW, PermissionCodes.DEPARTMENTS_MANAGE);
                yield departmentRepository.findAll(textSpec(q, "name", "description"), pageable).map(this::toDepartment);
            }
            case "positions" -> {
                permissionService.requireAny(actor, PermissionCodes.EMPLOYEES_VIEW, PermissionCodes.POSITIONS_MANAGE);
                yield positionRepository.findAll(textSpec(q, "title", "description"), pageable).map(this::toPosition);
            }
            case "contracts" -> {
                permissionService.require(actor, PermissionCodes.CONTRACTS_VIEW);
                yield contractRepository.findAll(statusSpec(q, filters, emp, own, "employeeId", List.of("notes", "type"), List.of("type"), visible), pageable).map(this::toContract);
            }
            case "attendance" -> {
                boolean company = permissionService.has(actor, PermissionCodes.ATTENDANCE_VIEW);
                if (!company && !emp) {
                    permissionService.require(actor, PermissionCodes.ATTENDANCE_VIEW);
                }
                if (!company && own == null) {
                    throw BusinessException.forbidden("Aucune fiche employé liée à ce compte");
                }
                yield attendanceRepository.findAll(attendanceSpec(q, filters, !company || emp, own, visible), pageable).map(this::toAttendance);
            }
            case "working-hours" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                yield workingHoursRepository.findAll(textSpec(q, "name", "workDays"), pageable).map(this::toWorkingHours);
            }
            case "leave-types" -> {
                permissionService.requireAny(actor, PermissionCodes.LEAVE_VIEW, PermissionCodes.SETTINGS_MANAGE);
                yield leaveTypeRepository.findAll(textSpec(q, "name", "code"), pageable).map(this::toLeaveType);
            }
            case "leave-balances" -> {
                permissionService.require(actor, PermissionCodes.LEAVE_VIEW);
                yield leaveBalanceRepository.findAll(employeeFilterSpec(filters, emp, own, visible), pageable).map(this::toLeaveBalance);
            }
            case "documents" -> {
                permissionService.require(actor, PermissionCodes.DOCUMENTS_VIEW);
                yield hrDocumentRepository.findAll(statusSpec(q, filters, emp, own, "employeeId", List.of("name"), List.of(), visible), pageable).map(this::toDocument);
            }
            case "employee-requests" -> {
                permissionService.requireAny(actor, PermissionCodes.REQUESTS_CREATE, PermissionCodes.REQUESTS_PROCESS);
                yield employeeRequestRepository.findAll(statusSpec(q, filters, emp, own, "employeeId", List.of("notes", "type"), List.of("type"), visible), pageable).map(this::toRequest);
            }
            case "announcements" -> {
                permissionService.require(actor, PermissionCodes.ANNOUNCEMENTS_VIEW);
                yield announcementRepository.findAll(textSpec(q, "title", "body"), pageable).map(this::toAnnouncement);
            }
            case "notifications" -> {
                boolean unreadOnly = "true".equalsIgnoreCase(filters.get("unread"));
                yield notificationRepository.findAll((root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.equal(root.get("userId"), actor.getId()));
                    if (unreadOnly) {
                        predicates.add(cb.isFalse(root.get("readFlag")));
                    }
                    return cb.and(predicates.toArray(Predicate[]::new));
                }, pageable).map(this::toNotification);
            }
            case "expenses" -> {
                permissionService.requireAny(actor, PermissionCodes.EXPENSES_VIEW, PermissionCodes.EMPLOYEES_VIEW, PermissionCodes.REQUESTS_CREATE);
                yield expenseRepository.findAll(statusSpec(q, filters, emp, own, "employeeId", List.of("notes", "category"), List.of(), visible), pageable).map(this::toExpense);
            }
            case "business-trips" -> {
                permissionService.require(actor, PermissionCodes.EXPENSES_VIEW);
                yield businessTripRepository.findAll(statusSpec(q, filters, emp, own, "employeeId", List.of("destination", "purpose"), List.of(), visible), pageable).map(this::toTrip);
            }
            case "performance-reviews" -> {
                requirePerformanceRead(actor);
                Specification<PerformanceReview> spec = statusSpec(q, filters, emp, own, "employeeId", List.of("period", "comments"), List.of(), visible);
                if (emp) {
                    spec = spec.and((root, query, cb) -> root.get("status").in("SHARED", "ACKNOWLEDGED"));
                }
                yield performanceReviewRepository.findAll(spec, pageable).map(this::toReview);
            }
            case "objectives" -> {
                requirePerformanceRead(actor);
                yield objectiveRepository.findAll(statusSpec(q, filters, emp, own, "employeeId", List.of("title"), List.of(), visible), pageable).map(this::toObjective);
            }
            case "job-offers" -> {
                permissionService.require(actor, PermissionCodes.RECRUITMENT_MANAGE);
                yield jobOfferRepository.findAll(statusSpec(q, filters, false, null, null, List.of("title", "city"), List.of(), null), pageable).map(this::toJob);
            }
            case "candidates" -> {
                permissionService.require(actor, PermissionCodes.RECRUITMENT_MANAGE);
                yield candidateRepository.findAll(statusSpec(q, filters, false, null, "jobOfferId", List.of("firstName", "lastName", "email"), List.of(), null), pageable).map(this::toCandidate);
            }
            case "onboarding" -> {
                requireChecklistRead(actor);
                yield onboardingTaskRepository.findAll(statusSpec(q, filters, emp, own, "employeeId", List.of("task"), List.of(), visible), pageable).map(this::toOnboarding);
            }
            case "offboarding" -> {
                requireChecklistRead(actor);
                yield offboardingTaskRepository.findAll(statusSpec(q, filters, emp, own, "employeeId", List.of("task"), List.of(), visible), pageable).map(this::toOffboarding);
            }
            case "holidays" -> {
                permissionService.requireAny(actor, PermissionCodes.SETTINGS_MANAGE, PermissionCodes.LEAVE_VIEW);
                yield holidayRepository.findAll(textSpec(q, "name", "type"), pageable).map(this::toHoliday);
            }
            case "audit-logs" -> {
                permissionService.require(actor, PermissionCodes.AUDIT_VIEW);
                yield auditLogRepository.findAll(textSpec(q, "action", "entityType", "userEmail"), pageable).map(this::toAudit);
            }
            case "payslips" -> {
                if (!permissionService.has(actor, PermissionCodes.PAYROLL_VIEW)
                        && !permissionService.has(actor, PermissionCodes.PAYSLIPS_VIEW_OWN)) {
                    throw BusinessException.forbidden("Permission requise : PAYSLIPS_VIEW_OWN");
                }
                boolean ownOnly = !permissionService.has(actor, PermissionCodes.PAYROLL_VIEW);
                List<Long> nameIds = null;
                if (StringUtils.hasText(q)) {
                    String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                    nameIds = employeeRepository.findAll((root, query, cb) -> cb.or(
                            cb.like(cb.lower(root.get("firstName")), like),
                            cb.like(cb.lower(root.get("lastName")), like),
                            cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like)
                    )).stream().map(Employee::getId).toList();
                }
                List<Long> matched = nameIds;
                Integer month = parseInt(filters.get("month"));
                Integer year = parseInt(filters.get("year"));
                yield payslipRepository.findAll((root, query, cb) -> {
                    List<Predicate> p = new ArrayList<>();
                    if (ownOnly && own != null) {
                        p.add(cb.equal(root.get("employeeId"), own));
                    }
                    if (month != null) {
                        p.add(cb.equal(root.get("month"), month));
                    }
                    if (year != null) {
                        p.add(cb.equal(root.get("year"), year));
                    }
                    Long slipEmployee = parseLong(filters.get("employeeId"));
                    if (slipEmployee != null) {
                        p.add(cb.equal(root.get("employeeId"), slipEmployee));
                    }
                    if (matched != null) {
                        if (matched.isEmpty()) {
                            return cb.disjunction();
                        }
                        p.add(root.get("employeeId").in(matched));
                    }
                    return p.isEmpty() ? cb.conjunction() : cb.and(p.toArray(Predicate[]::new));
                }, pageable).map(this::toPayslip);
            }
            default -> throw BusinessException.notFound("Ressource inconnue");
        };
    }

    @Transactional
    public Map<String, Object> create(String resource, Map<String, Object> body, UserPrincipal actor) {
        Long tenantId = TenantContext.requireTenantId();
        Map<String, Object> dto = switch (resource) {
            case "employees" -> {
                permissionService.require(actor, PermissionCodes.EMPLOYEES_CREATE);
                quotaService.assertEmployeeQuota();
                Employee e = new Employee();
                applyEmployee(e, body);
                e.setTenantId(tenantId);
                if (e.getStatus() == null) {
                    e.setStatus("ACTIVE");
                }
                yield toEmployee(employeeRepository.save(e));
            }
            case "departments" -> {
                permissionService.require(actor, PermissionCodes.DEPARTMENTS_MANAGE);
                Department d = new Department();
                applyDepartment(d, body);
                d.setTenantId(tenantId);
                yield toDepartment(departmentRepository.save(d));
            }
            case "positions" -> {
                permissionService.require(actor, PermissionCodes.POSITIONS_MANAGE);
                Position p = new Position();
                applyPosition(p, body);
                p.setTenantId(tenantId);
                yield toPosition(positionRepository.save(p));
            }
            case "contracts" -> {
                permissionService.require(actor, PermissionCodes.CONTRACTS_MANAGE);
                Contract c = new Contract();
                applyContract(c, body);
                c.setTenantId(tenantId);
                if (c.getStatus() == null) {
                    c.setStatus("ACTIVE");
                }
                yield toContract(finalizeContract(contractRepository.save(c)));
            }
            case "attendance" -> {
                permissionService.require(actor, PermissionCodes.ATTENDANCE_MANAGE);
                Attendance a = new Attendance();
                applyAttendance(a, body);
                a.setTenantId(tenantId);
                yield toAttendance(attendanceRepository.save(a));
            }
            case "working-hours" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                WorkingHours w = new WorkingHours();
                applyWorkingHours(w, body);
                w.setTenantId(tenantId);
                yield toWorkingHours(workingHoursRepository.save(w));
            }
            case "leave-types" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                LeaveType t = new LeaveType();
                applyLeaveType(t, body);
                t.setTenantId(tenantId);
                yield toLeaveType(leaveTypeRepository.save(t));
            }
            case "leave-balances" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                LeaveBalance b = new LeaveBalance();
                applyBalance(b, body);
                b.setTenantId(tenantId);
                if (b.getYear() == null) {
                    b.setYear(LocalDate.now().getYear());
                }
                yield toLeaveBalance(leaveBalanceRepository.save(b));
            }
            case "documents" -> {
                permissionService.require(actor, PermissionCodes.DOCUMENTS_UPLOAD);
                lockEmployeeSelf(actor, body, true);
                HrDocument d = new HrDocument();
                applyDocument(d, body);
                d.setTenantId(tenantId);
                yield toDocument(hrDocumentRepository.save(d));
            }
            case "employee-requests" -> {
                permissionService.require(actor, PermissionCodes.REQUESTS_CREATE);
                lockEmployeeSelf(actor, body, true);
                EmployeeRequest r = new EmployeeRequest();
                applyRequest(r, body);
                r.setTenantId(tenantId);
                if (r.getEmployeeId() == null) {
                    r.setEmployeeId(actor.getEmployeeId());
                }
                if (r.getStatus() == null
                        || (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.SUPER_ADMIN)) {
                    r.setStatus("PENDING");
                }
                r = employeeRequestRepository.save(r);
                notificationService.notifyAdmins("Demande RH", "Nouvelle demande " + r.getType(), "/app/requests");
                yield toRequest(r);
            }
            case "announcements" -> {
                permissionService.require(actor, PermissionCodes.ANNOUNCEMENTS_MANAGE);
                Announcement a = new Announcement();
                applyAnnouncement(a, body);
                a.setTenantId(tenantId);
                a = announcementRepository.save(a);
                notificationService.notifyCompany(a.getTitle(), a.getBody(), a.getDepartmentId());
                yield toAnnouncement(a);
            }
            case "expenses" -> {
                permissionService.require(actor, PermissionCodes.EXPENSES_VIEW);
                lockEmployeeSelf(actor, body, true);
                guardDecision(actor, body, true, JsonMaps.lng(body, "employeeId"));
                Expense e = new Expense();
                applyExpense(e, body);
                e.setTenantId(tenantId);
                if (e.getEmployeeId() == null) {
                    e.setEmployeeId(actor.getEmployeeId());
                }
                if (e.getStatus() == null) {
                    e.setStatus("PENDING");
                }
                assertExpense(e);
                e = expenseRepository.save(e);
                notificationService.notifyManagerOf(e.getEmployeeId(), "Note de frais",
                        "Une note de frais est en attente.", "/app/expenses");
                notificationService.emailManagerOf(e.getEmployeeId(), "Note de frais",
                        "Une note de frais est en attente de décision.", "/app/expenses", "Voir les notes");
                yield toExpense(e);
            }
            case "business-trips" -> {
                permissionService.require(actor, PermissionCodes.EXPENSES_VIEW);
                lockEmployeeSelf(actor, body, true);
                guardDecision(actor, body, true, JsonMaps.lng(body, "employeeId"));
                BusinessTrip t = new BusinessTrip();
                applyTrip(t, body);
                t.setTenantId(tenantId);
                if (t.getEmployeeId() == null) {
                    t.setEmployeeId(actor.getEmployeeId());
                }
                if (t.getStatus() == null) {
                    t.setStatus("PENDING");
                }
                assertTrip(t);
                t = businessTripRepository.save(t);
                notificationService.notifyManagerOf(t.getEmployeeId(), "Déplacement",
                        "Un déplacement est en attente.", "/app/trips");
                notificationService.emailManagerOf(t.getEmployeeId(), "Déplacement",
                        "Un déplacement est en attente de décision.", "/app/trips", "Voir les déplacements");
                yield toTrip(t);
            }
            case "performance-reviews" -> {
                permissionService.require(actor, PermissionCodes.PERFORMANCE_MANAGE);
                PerformanceReview r = new PerformanceReview();
                applyReview(r, body);
                if (!StringUtils.hasText(r.getStatus())) {
                    r.setStatus("DRAFT");
                }
                assertReview(r, actor);
                r.setTenantId(tenantId);
                r = performanceReviewRepository.save(r);
                if ("SHARED".equals(r.getStatus())) {
                    notifyReviewShared(r);
                }
                yield toReview(r);
            }
            case "objectives" -> {
                permissionService.require(actor, PermissionCodes.PERFORMANCE_MANAGE);
                Objective o = new Objective();
                applyObjective(o, body);
                if (o.getProgress() == null) {
                    o.setProgress(0);
                }
                assertObjective(o, actor);
                o.setTenantId(tenantId);
                o = objectiveRepository.save(o);
                notificationService.notifyEmployee(o.getEmployeeId(), "Objectif", "Un objectif vous a été fixé : " + o.getTitle(), "/portal/objectives");
                notificationService.emailEmployee(o.getEmployeeId(), "Objectif", "Un objectif vous a été fixé : " + o.getTitle(), "/portal/objectives", "Voir l’objectif");
                yield toObjective(o);
            }
            case "job-offers" -> {
                permissionService.require(actor, PermissionCodes.RECRUITMENT_MANAGE);
                JobOffer o = new JobOffer();
                applyJob(o, body);
                if (!StringUtils.hasText(o.getStatus())) {
                    o.setStatus("OPEN");
                }
                assertJob(o);
                o.setTenantId(tenantId);
                yield toJob(jobOfferRepository.save(o));
            }
            case "candidates" -> {
                permissionService.require(actor, PermissionCodes.RECRUITMENT_MANAGE);
                Candidate c = new Candidate();
                applyCandidate(c, body);
                if (!StringUtils.hasText(c.getStatus())) {
                    c.setStatus("NEW");
                }
                assertCandidate(c, null);
                c.setTenantId(tenantId);
                c = candidateRepository.save(c);
                notifyCandidate(c, null);
                yield toCandidate(c);
            }
            case "onboarding" -> {
                permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
                OnboardingTask t = new OnboardingTask();
                applyOnboarding(t, body);
                if (!StringUtils.hasText(t.getStatus())) {
                    t.setStatus("PENDING");
                }
                assertChecklist(t.getEmployeeId(), t.getTask(), t.getStatus(), actor);
                t.setTenantId(tenantId);
                t = onboardingTaskRepository.save(t);
                notifyChecklistAssigned(t.getEmployeeId(), t.getTask(), true);
                yield toOnboarding(t);
            }
            case "offboarding" -> {
                permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
                OffboardingTask t = new OffboardingTask();
                applyOffboarding(t, body);
                if (!StringUtils.hasText(t.getStatus())) {
                    t.setStatus("PENDING");
                }
                assertChecklist(t.getEmployeeId(), t.getTask(), t.getStatus(), actor);
                t.setTenantId(tenantId);
                t = offboardingTaskRepository.save(t);
                notifyChecklistAssigned(t.getEmployeeId(), t.getTask(), false);
                yield toOffboarding(t);
            }
            case "holidays" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                Holiday h = new Holiday();
                applyHoliday(h, body);
                h.setTenantId(tenantId);
                yield toHoliday(holidayRepository.save(h));
            }
            default -> throw BusinessException.notFound("Création non supportée");
        };
        auditService.log(actor, "CREATE", resource, dto.get("id"), resource);
        return dto;
    }

    @Transactional
    public Map<String, Object> hire(Map<String, Object> body, StoredFile file, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.EMPLOYEES_CREATE);
        permissionService.require(actor, PermissionCodes.CONTRACTS_MANAGE);
        quotaService.assertEmployeeQuota();
        Long tenantId = TenantContext.requireTenantId();
        Employee employee = new Employee();
        applyEmployee(employee, body);
        employee.setTenantId(tenantId);
        if (employee.getStatus() == null) {
            employee.setStatus("ACTIVE");
        }
        Map<String, Object> contractBody = mapOf(body.get("contract"));
        String type = JsonMaps.str(contractBody, "type");
        if (!StringUtils.hasText(type)) {
            type = "CDI";
        }
        java.time.LocalDate start = JsonMaps.date(contractBody, "startDate");
        if (start == null) {
            start = employee.getHireDate();
        }
        if (start == null) {
            start = java.time.LocalDate.now();
        }
        if (employee.getHireDate() == null) {
            employee.setHireDate(start);
        }
        java.math.BigDecimal salary = JsonMaps.dec(contractBody, "baseSalary");
        if (salary == null) {
            throw BusinessException.badRequest("Le salaire de base du contrat est obligatoire");
        }
        java.time.LocalDate end = JsonMaps.date(contractBody, "endDate");
        if (end == null && ("CDD".equals(type) || "STAGE".equals(type) || "INTERIM".equals(type))) {
            throw BusinessException.badRequest("La date de fin est obligatoire pour un CDD, un stage ou un intérim");
        }
        employee = employeeRepository.save(employee);

        Contract contract = new Contract();
        contract.setTenantId(tenantId);
        contract.setEmployeeId(employee.getId());
        contract.setType(type);
        contract.setStartDate(start);
        contract.setEndDate(end);
        contract.setBaseSalary(salary);
        java.math.BigDecimal hours = JsonMaps.dec(contractBody, "weeklyHours");
        contract.setWeeklyHours(hours == null ? new java.math.BigDecimal("44") : hours);
        contract.setDepartmentId(employee.getDepartmentId());
        contract.setPositionId(employee.getPositionId());
        contract.setStatus("ACTIVE");
        contract.setNotes(JsonMaps.str(contractBody, "notes"));
        contract.setTrialStart(JsonMaps.date(contractBody, "trialStart"));
        contract.setTrialEnd(JsonMaps.date(contractBody, "trialEnd"));
        contract = finalizeContract(contractRepository.save(contract));

        if (file != null) {
            HrDocument doc = saveFile(tenantId, employee.getId(), contract.getId(), "CONTRACT", file, true);
            contract.setDocumentId(doc.getId());
            contract = contractRepository.save(contract);
        }

        Long hiredId = employee.getId();
        int hiredYear = start.getYear();
        leaveTypeRepository.findAll().stream()
                .filter(t -> "ANNUAL".equalsIgnoreCase(t.getCode()))
                .findFirst()
                .ifPresent(typeAnnual -> {
                    LeaveBalance balance = new LeaveBalance();
                    balance.setTenantId(tenantId);
                    balance.setEmployeeId(hiredId);
                    balance.setLeaveTypeId(typeAnnual.getId());
                    balance.setYear(hiredYear);
                    balance.setAccrued(java.math.BigDecimal.ZERO);
                    balance.setUsed(java.math.BigDecimal.ZERO);
                    balance.setRemaining(java.math.BigDecimal.ZERO);
                    leaveBalanceRepository.save(balance);
                });

        seedChecklist(tenantId, employee.getId(), start.plusDays(14), true);

        auditService.log(actor, "CREATE", "employees", employee.getId(), "onboard");
        Map<String, Object> dto = toEmployee(employee);
        dto.put("contractId", contract.getId());
        dto.put("contract", toContract(contract));
        return dto;
    }

    @Transactional
    public Map<String, Object> attachDocument(Long employeeId, Long contractId, String category, String name,
            boolean visible, StoredFile file, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.DOCUMENTS_UPLOAD);
        if (isPrivateCategory(category) && actor.getRole() != UserRole.ADMIN
                && !permissionService.has(actor, PermissionCodes.DOCUMENTS_PRIVATE)) {
            throw BusinessException.forbidden("Documents médicaux ou CIN : permission DOCUMENTS_PRIVATE requise");
        }
        if (file == null) {
            throw BusinessException.badRequest("Fichier obligatoire");
        }
        Long tenantId = TenantContext.requireTenantId();
        Employee employee = employeeId == null ? null : employeeRepository.findById(employeeId)
                .orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
        HrDocument doc = saveFile(tenantId, employeeId, contractId,
                StringUtils.hasText(category) ? category : "OTHER", file, visible);
        if (StringUtils.hasText(name)) {
            doc.setName(name);
            doc = hrDocumentRepository.save(doc);
        }
        if (contractId != null) {
            Contract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> BusinessException.notFound("Contrat introuvable"));
            contract.setDocumentId(doc.getId());
            contractRepository.save(contract);
        }
        if (visible && employee != null) {
            notificationService.emailEmployee(employee.getId(), "Document disponible",
                    "Un document a été ajouté à votre dossier.", "/portal/documents", "Voir le document");
        }
        auditService.log(actor, "UPLOAD", "documents", doc.getId(), employee == null ? null : employee.getEmail());
        return toDocument(doc);
    }

    @Transactional
    public byte[] documentBytes(Long id, UserPrincipal actor) {
        HrDocument doc = hrDocumentRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Document introuvable"));
        boolean own = actor.getEmployeeId() != null && actor.getEmployeeId().equals(doc.getEmployeeId());
        if (!permissionService.has(actor, PermissionCodes.DOCUMENTS_VIEW) && !own) {
            throw BusinessException.forbidden("Accès document refusé");
        }
        if (isPrivateCategory(doc.getCategory()) && !own
                && !permissionService.has(actor, PermissionCodes.DOCUMENTS_PRIVATE)
                && actor.getRole() != UserRole.ADMIN) {
            throw BusinessException.forbidden("Document privé");
        }
        if (own && !doc.isVisibleToEmployee() && !permissionService.has(actor, PermissionCodes.DOCUMENTS_PRIVATE)) {
            throw BusinessException.forbidden("Document non visible");
        }
        if (StringUtils.hasText(doc.getStoragePath())) {
            try {
                return fileStorageService.read(doc.getStoragePath());
            } catch (BusinessException ex) {
                if (!canGenerate(doc)) {
                    throw ex;
                }
            }
        } else if (!canGenerate(doc)) {
            throw BusinessException.notFound("Aucune pièce jointe");
        }
        return materializeDocument(doc);
    }

    @Transactional
    public byte[] requestPdf(Long id, UserPrincipal actor) {
        EmployeeRequest r = employeeRequestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Demande introuvable"));
        boolean own = actor.getEmployeeId() != null && actor.getEmployeeId().equals(r.getEmployeeId());
        boolean processor = permissionService.has(actor, PermissionCodes.REQUESTS_PROCESS);
        if (!own && !processor) {
            permissionService.requireAny(actor, PermissionCodes.REQUESTS_CREATE, PermissionCodes.REQUESTS_PROCESS);
            scopeService.assertEmployeeVisible(actor, r.getEmployeeId());
        }
        String type = r.getType() == null ? "" : r.getType().toUpperCase(Locale.ROOT);
        boolean salary = type.contains("SALARY") || type.contains("SALAIRE");
        if (salary && !own && !processor) {
            throw BusinessException.forbidden("L’attestation de salaire est réservée à l’employé et à l’administrateur");
        }
        String status = r.getStatus() == null ? "" : r.getStatus();
        if ("REJECTED".equals(status) || "CANCELLED".equals(status)) {
            throw BusinessException.badRequest("Aucun PDF pour une demande refusée");
        }
        if (("PENDING".equals(status) || "PROCESSING".equals(status)) && !processor) {
            throw BusinessException.badRequest("Le PDF sera disponible une fois la demande traitée");
        }
        if (r.getDocumentId() != null) {
            HrDocument existing = hrDocumentRepository.findById(r.getDocumentId()).orElse(null);
            if (existing != null && StringUtils.hasText(existing.getStoragePath())) {
                try {
                    return fileStorageService.read(existing.getStoragePath());
                } catch (BusinessException ignored) {
                    // Le fichier stocké est illisible : on régénère.
                }
            }
        }
        Employee employee = employeeRepository.findById(r.getEmployeeId())
                .orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
        byte[] pdf = pdfDocumentService.attestation(employee, r.getType());
        StoredFile stored = fileStorageService.storeBytes(
                pdf, "application/pdf", "attestation-" + employee.getId() + ".pdf");
        HrDocument doc = saveFile(r.getTenantId(), employee.getId(), null, "ATTESTATION", stored, true);
        r.setDocumentId(doc.getId());
        if (!"COMPLETED".equals(status) && !"APPROVED".equals(status)) {
            r.setStatus("COMPLETED");
        }
        employeeRequestRepository.save(r);
        return pdf;
    }

    @Transactional
    public byte[] contractPdf(Long id, UserPrincipal actor) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Contrat introuvable"));
        boolean own = actor.getEmployeeId() != null && actor.getEmployeeId().equals(contract.getEmployeeId());
        if (!own) {
            permissionService.require(actor, PermissionCodes.CONTRACTS_VIEW);
            scopeService.assertEmployeeVisible(actor, contract.getEmployeeId());
        }
        if (contract.getDocumentId() != null) {
            try {
                return documentBytes(contract.getDocumentId(), actor);
            } catch (BusinessException ex) {
                if (ex.getStatus() != org.springframework.http.HttpStatus.NOT_FOUND) {
                    throw ex;
                }
            }
        }
        Employee employee = employeeRepository.findById(contract.getEmployeeId())
                .orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
        String extra = "Contrat " + (contract.getType() == null ? "" : contract.getType())
                + " à compter du " + contract.getStartDate()
                + (contract.getEndDate() == null ? "" : ", jusqu’au " + contract.getEndDate())
                + (contract.getBaseSalary() == null ? "" : ". Salaire de base : " + contract.getBaseSalary().toPlainString() + " MAD");
        return pdfDocumentService.attestation(employee, "CONTRAT", extra);
    }

    private boolean canGenerate(HrDocument doc) {
        if (doc.getEmployeeId() == null || isPrivateCategory(doc.getCategory())) {
            return false;
        }
        String cat = doc.getCategory() == null ? "" : doc.getCategory().toUpperCase(Locale.ROOT);
        return cat.contains("CONTRACT") || cat.contains("CONTRAT")
                || cat.contains("ATTESTATION") || cat.contains("CERTIFICATE");
    }

    private byte[] materializeDocument(HrDocument doc) {
        Employee employee = employeeRepository.findById(doc.getEmployeeId())
                .orElseThrow(() -> BusinessException.notFound("Aucune pièce jointe"));
        String cat = doc.getCategory() == null ? "" : doc.getCategory().toUpperCase(Locale.ROOT);
        String kind = cat.contains("CONTRACT") || cat.contains("CONTRAT") ? "CONTRAT" : cat;
        byte[] pdf = pdfDocumentService.attestation(employee, kind);
        StoredFile stored = fileStorageService.storeBytes(pdf, "application/pdf", "document-" + doc.getId() + ".pdf");
        doc.setStoragePath(stored.storedName());
        doc.setContentType("application/pdf");
        doc.setOriginalName(stored.originalName());
        doc.setSizeBytes(stored.size());
        if (!StringUtils.hasText(doc.getName())) {
            doc.setName(stored.originalName());
        }
        hrDocumentRepository.save(doc);
        return pdf;
    }

    public HrDocument documentMeta(Long id, UserPrincipal actor) {
        documentBytes(id, actor);
        return hrDocumentRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Document introuvable"));
    }

    private HrDocument saveFile(Long tenantId, Long employeeId, Long contractId, String category, StoredFile file, boolean visible) {
        HrDocument doc = new HrDocument();
        doc.setTenantId(tenantId);
        doc.setEmployeeId(employeeId);
        doc.setContractId(contractId);
        doc.setCategory(category);
        doc.setName(file.originalName());
        doc.setOriginalName(file.originalName());
        doc.setStoragePath(file.storedName());
        doc.setContentType(file.contentType());
        doc.setSizeBytes(file.size());
        doc.setVisibleToEmployee(visible);
        doc.setPrivateDoc(isPrivateCategory(category));
        return hrDocumentRepository.save(doc);
    }

    private boolean isPrivateCategory(String category) {
        if (category == null) {
            return false;
        }
        String c = category.toUpperCase(Locale.ROOT);
        return c.contains("MEDICAL") || c.contains("CIN") || c.contains("IDENTITE") || c.contains("IDENTITY");
    }

    @Transactional
    public Map<String, Object> processRequest(Long id, Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.REQUESTS_PROCESS);
        EmployeeRequest r = employeeRequestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Demande introuvable"));
        String action = JsonMaps.str(body, "action");
        boolean admin = actor.getRole() == ma.rhflow.domain.enums.UserRole.ADMIN
                || actor.getRole() == ma.rhflow.domain.enums.UserRole.SUPER_ADMIN;
        scopeService.assertEmployeeVisible(actor, r.getEmployeeId());
        if ("reject".equalsIgnoreCase(action)) {
            if (!admin && !"PENDING".equals(r.getStatus())) {
                throw BusinessException.forbidden("Seul un administrateur peut refuser une demande déjà validée");
            }
            r.setStatus("REJECTED");
            r.setApprovalStep(0);
            r.setNotes(JsonMaps.str(body, "notes") == null ? r.getNotes() : JsonMaps.str(body, "notes"));
            employeeRequestRepository.save(r);
            notificationService.notifyEmployee(r.getEmployeeId(), "Demande refusée", "Votre demande RH a été refusée.", "/portal/requests");
            notificationService.emailEmployee(r.getEmployeeId(), "Demande refusée",
                    "Votre demande RH a été refusée.", "/portal/requests", "Voir la demande");
            return toRequest(r);
        }
        Employee employee = employeeRepository.findById(r.getEmployeeId())
                .orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
        if ("ATTESTATION_SALAIRE".equalsIgnoreCase(r.getType()) || "SALARY".equalsIgnoreCase(r.getType())) {
            permissionService.requireAny(actor, PermissionCodes.REQUESTS_PROCESS);
        }
        if (!admin && "PENDING".equals(r.getStatus())) {
            r.setStatus("MANAGER_APPROVED");
            r.setApprovalStep(1);
            employeeRequestRepository.save(r);
            notificationService.emailAdmins("Demande RH à valider", "Validation manager effectuée.", "/app/requests", "Ouvrir");
            return toRequest(r);
        }
        if (!admin) {
            throw BusinessException.forbidden("Seul un administrateur émet l’attestation");
        }
        byte[] pdf = pdfDocumentService.attestation(employee, r.getType());
        StoredFile stored = fileStorageService.storeBytes(
                pdf, "application/pdf", "attestation-" + employee.getId() + ".pdf");
        HrDocument doc = saveFile(r.getTenantId(), employee.getId(), null, "ATTESTATION", stored, true);
        r.setDocumentId(doc.getId());
        r.setStatus("COMPLETED");
        r.setApprovalStep(2);
        employeeRequestRepository.save(r);
        notificationService.notifyEmployee(employee.getId(), "Document disponible",
                "Votre attestation a été générée.", "/portal/documents");
        notificationService.emailEmployee(employee.getId(), "Document disponible",
                "Votre attestation a été générée.", "/portal/documents", "Voir le document");
        Map<String, Object> dto = toRequest(r);
        dto.put("documentId", doc.getId());
        return dto;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @Transactional
    public Map<String, Object> update(String resource, Long id, Map<String, Object> body, UserPrincipal actor) {
        Map<String, Object> dto = switch (resource) {
            case "employees" -> {
                permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
                Employee e = employeeRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
                if ((body.containsKey("medicalNotes") || body.containsKey("bloodType"))
                        && !permissionService.has(actor, PermissionCodes.DOCUMENTS_PRIVATE)) {
                    throw BusinessException.forbidden("Le dossier médical exige la permission DOCUMENTS_PRIVATE");
                }
                applyEmployee(e, body);
                if (body.containsKey("bloodType")) e.setBloodType(JsonMaps.str(body, "bloodType"));
                if (body.containsKey("medicalNotes")) e.setMedicalNotes(JsonMaps.str(body, "medicalNotes"));
                yield employeeView(employeeRepository.save(e), actor);
            }
            case "departments" -> {
                permissionService.require(actor, PermissionCodes.DEPARTMENTS_MANAGE);
                Department d = departmentRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Département introuvable"));
                applyDepartment(d, body);
                yield toDepartment(departmentRepository.save(d));
            }
            case "positions" -> {
                permissionService.require(actor, PermissionCodes.POSITIONS_MANAGE);
                Position p = positionRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Poste introuvable"));
                applyPosition(p, body);
                yield toPosition(positionRepository.save(p));
            }
            case "contracts" -> {
                permissionService.require(actor, PermissionCodes.CONTRACTS_MANAGE);
                Contract c = contractRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Contrat introuvable"));
                applyContract(c, body);
                yield toContract(finalizeContract(contractRepository.save(c)));
            }
            case "attendance" -> {
                permissionService.require(actor, PermissionCodes.ATTENDANCE_MANAGE);
                Attendance a = attendanceRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Présence introuvable"));
                applyAttendance(a, body);
                yield toAttendance(attendanceRepository.save(a));
            }
            case "working-hours" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                WorkingHours w = workingHoursRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Horaire introuvable"));
                applyWorkingHours(w, body);
                yield toWorkingHours(workingHoursRepository.save(w));
            }
            case "leave-types" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                LeaveType t = leaveTypeRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Type introuvable"));
                applyLeaveType(t, body);
                yield toLeaveType(leaveTypeRepository.save(t));
            }
            case "leave-balances" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                LeaveBalance b = leaveBalanceRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Solde introuvable"));
                applyBalance(b, body);
                yield toLeaveBalance(leaveBalanceRepository.save(b));
            }
            case "documents" -> {
                permissionService.require(actor, PermissionCodes.DOCUMENTS_UPLOAD);
                lockEmployeeSelf(actor, body, false);
                HrDocument d = hrDocumentRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Document introuvable"));
                applyDocument(d, body);
                yield toDocument(hrDocumentRepository.save(d));
            }
            case "employee-requests" -> {
                lockEmployeeSelf(actor, body, false);
                if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.SUPER_ADMIN) {
                    body.remove("status");
                }
                EmployeeRequest r = employeeRequestRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Demande introuvable"));
                applyRequest(r, body);
                yield toRequest(employeeRequestRepository.save(r));
            }
            case "announcements" -> {
                permissionService.require(actor, PermissionCodes.ANNOUNCEMENTS_MANAGE);
                Announcement a = announcementRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Annonce introuvable"));
                applyAnnouncement(a, body);
                yield toAnnouncement(announcementRepository.save(a));
            }
            case "expenses" -> {
                permissionService.require(actor, PermissionCodes.EXPENSES_VIEW);
                lockEmployeeSelf(actor, body, false);
                Expense e = expenseRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Frais introuvable"));
                String before = e.getStatus();
                guardDecision(actor, body, false, e.getEmployeeId());
                applyExpense(e, body);
                assertExpense(e);
                e = expenseRepository.save(e);
                notifyDecision(e.getEmployeeId(), before, e.getStatus(), "Note de frais", "/portal/expenses");
                yield toExpense(e);
            }
            case "business-trips" -> {
                permissionService.require(actor, PermissionCodes.EXPENSES_VIEW);
                lockEmployeeSelf(actor, body, false);
                BusinessTrip t = businessTripRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Déplacement introuvable"));
                String before = t.getStatus();
                guardDecision(actor, body, false, t.getEmployeeId());
                applyTrip(t, body);
                assertTrip(t);
                t = businessTripRepository.save(t);
                notifyDecision(t.getEmployeeId(), before, t.getStatus(), "Déplacement", "/portal/trips");
                yield toTrip(t);
            }
            case "performance-reviews" -> {
                PerformanceReview r = performanceReviewRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Entretien introuvable"));
                scopeService.assertEmployeeVisible(actor, r.getEmployeeId());
                String before = r.getStatus();
                if (actor.getRole() == UserRole.EMPLOYEE) {
                    acknowledgeReview(actor, r, body);
                } else {
                    permissionService.require(actor, PermissionCodes.PERFORMANCE_MANAGE);
                    applyReview(r, body);
                    assertReview(r, actor);
                }
                r = performanceReviewRepository.save(r);
                if (!"SHARED".equals(before) && "SHARED".equals(r.getStatus())) {
                    notifyReviewShared(r);
                }
                if (!"ACKNOWLEDGED".equals(before) && "ACKNOWLEDGED".equals(r.getStatus())) {
                    notificationService.notifyManagerOf(r.getEmployeeId(), "Entretien", "L’entretien a été accusé réception.", "/app/reviews");
                }
                yield toReview(r);
            }
            case "objectives" -> {
                Objective o = objectiveRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Objectif introuvable"));
                scopeService.assertEmployeeVisible(actor, o.getEmployeeId());
                int before = o.getProgress() == null ? 0 : o.getProgress();
                if (actor.getRole() == UserRole.EMPLOYEE) {
                    updateOwnProgress(actor, o, body);
                } else {
                    permissionService.require(actor, PermissionCodes.PERFORMANCE_MANAGE);
                    applyObjective(o, body);
                    assertObjective(o, actor);
                }
                o = objectiveRepository.save(o);
                int after = o.getProgress() == null ? 0 : o.getProgress();
                if (before < 100 && after == 100) {
                    notificationService.notifyManagerOf(o.getEmployeeId(), "Objectif atteint", o.getTitle(), "/app/objectives");
                }
                yield toObjective(o);
            }
            case "job-offers" -> {
                permissionService.require(actor, PermissionCodes.RECRUITMENT_MANAGE);
                JobOffer o = jobOfferRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Offre introuvable"));
                applyJob(o, body);
                assertJob(o);
                yield toJob(jobOfferRepository.save(o));
            }
            case "candidates" -> {
                permissionService.require(actor, PermissionCodes.RECRUITMENT_MANAGE);
                Candidate c = candidateRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Candidat introuvable"));
                String before = c.getStatus();
                applyCandidate(c, body);
                assertCandidate(c, before);
                c = candidateRepository.save(c);
                notifyCandidate(c, before);
                yield toCandidate(c);
            }
            case "onboarding" -> {
                OnboardingTask t = onboardingTaskRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Tâche introuvable"));
                scopeService.assertEmployeeVisible(actor, t.getEmployeeId());
                String before = t.getStatus();
                if (actor.getRole() == UserRole.EMPLOYEE) {
                    completeOwnTask(actor, t.getEmployeeId(), body);
                    t.setStatus("DONE");
                } else {
                    permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
                    applyOnboarding(t, body);
                    assertChecklist(t.getEmployeeId(), t.getTask(), t.getStatus(), actor);
                }
                t = onboardingTaskRepository.save(t);
                if (!"DONE".equals(before) && "DONE".equals(t.getStatus())) {
                    notificationService.notifyAdmins("Intégration", "Une tâche d’intégration est faite.", "/app/onboarding");
                    notifyChecklistComplete(t.getEmployeeId(), true);
                }
                yield toOnboarding(t);
            }
            case "offboarding" -> {
                OffboardingTask t = offboardingTaskRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Tâche introuvable"));
                scopeService.assertEmployeeVisible(actor, t.getEmployeeId());
                String before = t.getStatus();
                if (actor.getRole() == UserRole.EMPLOYEE) {
                    completeOwnTask(actor, t.getEmployeeId(), body);
                    t.setStatus("DONE");
                } else {
                    permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
                    applyOffboarding(t, body);
                    assertChecklist(t.getEmployeeId(), t.getTask(), t.getStatus(), actor);
                }
                t = offboardingTaskRepository.save(t);
                if (!"DONE".equals(before) && "DONE".equals(t.getStatus())) {
                    notificationService.notifyAdmins("Départ", "Une tâche de départ est faite.", "/app/offboarding");
                    notifyChecklistComplete(t.getEmployeeId(), false);
                }
                yield toOffboarding(t);
            }
            case "holidays" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                Holiday h = holidayRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Jour férié introuvable"));
                applyHoliday(h, body);
                yield toHoliday(holidayRepository.save(h));
            }
            default -> throw BusinessException.notFound("Mise à jour non supportée");
        };
        auditService.log(actor, "UPDATE", resource, id, resource);
        return dto;
    }

    @Transactional
    public void delete(String resource, Long id, UserPrincipal actor) {
        switch (resource) {
            case "employees" -> {
                permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
                employeeRepository.deleteById(id);
            }
            case "departments" -> {
                permissionService.require(actor, PermissionCodes.DEPARTMENTS_MANAGE);
                departmentRepository.deleteById(id);
            }
            case "positions" -> {
                permissionService.require(actor, PermissionCodes.POSITIONS_MANAGE);
                positionRepository.deleteById(id);
            }
            case "contracts" -> {
                permissionService.require(actor, PermissionCodes.CONTRACTS_MANAGE);
                contractRepository.deleteById(id);
            }
            case "attendance" -> {
                permissionService.require(actor, PermissionCodes.ATTENDANCE_MANAGE);
                attendanceRepository.deleteById(id);
            }
            case "working-hours" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                workingHoursRepository.deleteById(id);
            }
            case "leave-types" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                leaveTypeRepository.deleteById(id);
            }
            case "leave-balances" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                leaveBalanceRepository.deleteById(id);
            }
            case "documents" -> {
                permissionService.require(actor, PermissionCodes.DOCUMENTS_UPLOAD);
                hrDocumentRepository.deleteById(id);
            }
            case "employee-requests" -> {
                permissionService.require(actor, PermissionCodes.REQUESTS_PROCESS);
                if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.SUPER_ADMIN) {
                    throw BusinessException.forbidden("Seul un administrateur supprime une demande RH");
                }
                employeeRequestRepository.deleteById(id);
            }
            case "announcements" -> {
                permissionService.require(actor, PermissionCodes.ANNOUNCEMENTS_MANAGE);
                announcementRepository.deleteById(id);
            }
            case "expenses" -> {
                Expense e = expenseRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Frais introuvable"));
                assertCanDeleteClaim(actor, e.getEmployeeId(), e.getStatus());
                expenseRepository.delete(e);
            }
            case "business-trips" -> {
                BusinessTrip t = businessTripRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Déplacement introuvable"));
                assertCanDeleteClaim(actor, t.getEmployeeId(), t.getStatus());
                businessTripRepository.delete(t);
            }
            case "performance-reviews" -> {
                permissionService.require(actor, PermissionCodes.PERFORMANCE_MANAGE);
                PerformanceReview r = performanceReviewRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Entretien introuvable"));
                scopeService.assertEmployeeVisible(actor, r.getEmployeeId());
                assertNotOwnPerformance(actor, r.getEmployeeId());
                performanceReviewRepository.delete(r);
            }
            case "objectives" -> {
                permissionService.require(actor, PermissionCodes.PERFORMANCE_MANAGE);
                Objective o = objectiveRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Objectif introuvable"));
                scopeService.assertEmployeeVisible(actor, o.getEmployeeId());
                assertNotOwnPerformance(actor, o.getEmployeeId());
                objectiveRepository.delete(o);
            }
            case "job-offers" -> {
                permissionService.require(actor, PermissionCodes.RECRUITMENT_MANAGE);
                if (candidateRepository.countByJobOfferId(id) > 0) {
                    throw BusinessException.badRequest("Retirez d’abord les candidats de cette offre");
                }
                jobOfferRepository.deleteById(id);
            }
            case "candidates" -> {
                permissionService.require(actor, PermissionCodes.RECRUITMENT_MANAGE);
                candidateRepository.deleteById(id);
            }
            case "onboarding" -> {
                permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
                OnboardingTask t = onboardingTaskRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Tâche introuvable"));
                scopeService.assertEmployeeVisible(actor, t.getEmployeeId());
                onboardingTaskRepository.delete(t);
            }
            case "offboarding" -> {
                permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
                OffboardingTask t = offboardingTaskRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Tâche introuvable"));
                scopeService.assertEmployeeVisible(actor, t.getEmployeeId());
                offboardingTaskRepository.delete(t);
            }
            case "holidays" -> {
                permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
                holidayRepository.deleteById(id);
            }
            default -> throw BusinessException.notFound("Suppression non supportée");
        }
        auditService.log(actor, "DELETE", resource, id, resource);
    }

    @Transactional
    public Map<String, Object> setPhoto(Long id, FileStorageService.StoredFile stored, UserPrincipal actor) {
        Employee employee = visibleEmployee(id, actor, true);
        String previous = employee.getPhotoPath();
        employee.setPhotoPath(stored.storedName());
        employeeRepository.save(employee);
        if (previous != null && !previous.equals(stored.storedName())) {
            fileStorageService.delete(previous);
        }
        auditService.log(actor, "UPDATE", "employees", id, "photo");
        return toEmployee(employee);
    }

    @Transactional(readOnly = true)
    public SettingsService.ImageFile photo(Long id, UserPrincipal actor) {
        Employee employee = visibleEmployee(id, actor, false);
        if (employee.getPhotoPath() == null || employee.getPhotoPath().isBlank()) {
            throw BusinessException.notFound("Aucune photo");
        }
        return new SettingsService.ImageFile(fileStorageService.read(employee.getPhotoPath()), SettingsService.media(employee.getPhotoPath()));
    }

    private Employee visibleEmployee(Long id, UserPrincipal actor, boolean write) {
        boolean self = actor.getEmployeeId() != null && actor.getEmployeeId().equals(id);
        if (!self) {
            permissionService.require(actor, write ? PermissionCodes.EMPLOYEES_EDIT : PermissionCodes.EMPLOYEES_VIEW);
            scopeService.assertEmployeeVisible(actor, id);
        }
        return employeeRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
    }

    public Map<String, Object> get(String resource, Long id, UserPrincipal actor) {
        if ("employees".equals(resource)) {
            permissionService.require(actor, PermissionCodes.EMPLOYEES_VIEW);
            Employee employee = employeeRepository.findById(id)
                    .orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
            if (actor.getRole() == UserRole.EMPLOYEE && actor.getEmployeeId() != null
                    && !actor.getEmployeeId().equals(employee.getId())) {
                throw BusinessException.forbidden("Accès employé refusé");
            }
            scopeService.assertEmployeeVisible(actor, employee.getId());
            return employeeView(employee, actor);
        }
        Page<Map<String, Object>> page = list(resource, Map.of(), 0, 200, actor);
        return page.getContent().stream().filter(m -> id.equals(toLong(m.get("id"))))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("Introuvable"));
    }

    private Long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private <T> Specification<T> textSpec(String q, String... fields) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(q)) {
                return cb.conjunction();
            }
            String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
            List<Predicate> ors = new ArrayList<>();
            for (String f : fields) {
                ors.add(cb.like(cb.lower(cb.coalesce(root.get(f).as(String.class), "")), like));
            }
            return cb.or(ors.toArray(Predicate[]::new));
        };
    }

    private <T> Specification<T> statusSpec(
            String q,
            Map<String, String> filters,
            boolean emp,
            Long own,
            String empField,
            List<String> textFields,
            List<String> equalsFields,
            Set<Long> visible) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (StringUtils.hasText(q) && textFields != null && !textFields.isEmpty()) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                List<Predicate> ors = new ArrayList<>();
                for (String field : textFields) {
                    ors.add(cb.like(cb.lower(cb.coalesce(root.get(field).as(String.class), "")), like));
                }
                p.add(cb.or(ors.toArray(Predicate[]::new)));
            }
            if (StringUtils.hasText(filters.get("status"))) {
                p.add(cb.equal(root.get("status"), filters.get("status")));
            }
            if (StringUtils.hasText(filters.get("category"))) {
                String like = "%" + filters.get("category").toLowerCase(Locale.ROOT) + "%";
                p.add(cb.like(cb.lower(cb.coalesce(root.get("category").as(String.class), "")), like));
            }
            if (equalsFields != null) {
                for (String field : equalsFields) {
                    if (StringUtils.hasText(filters.get(field))) {
                        p.add(cb.equal(root.get(field), filters.get(field)));
                    }
                }
            }
            if (emp && own != null && empField != null && "employeeId".equals(empField)) {
                p.add(cb.equal(root.get(empField), own));
            } else if (visible != null && empField != null && "employeeId".equals(empField)) {
                p.add(root.get(empField).in(visible));
            }
            if ("employeeId".equals(empField) || "jobOfferId".equals(empField)) {
                Long id = parseLong(filters.get(empField));
                if (id != null) {
                    p.add(cb.equal(root.get(empField), id));
                }
            }
            return p.isEmpty() ? cb.conjunction() : cb.and(p.toArray(Predicate[]::new));
        };
    }

    private <T> Specification<T> employeeFilterSpec(Map<String, String> filters, boolean emp, Long own, Set<Long> visible) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (emp && own != null) {
                p.add(cb.equal(root.get("employeeId"), own));
            } else if (visible != null) {
                p.add(root.get("employeeId").in(visible));
            }
            Long empId = parseLong(filters.get("employeeId"));
            if (empId != null) {
                p.add(cb.equal(root.get("employeeId"), empId));
            }
            return p.isEmpty() ? cb.conjunction() : cb.and(p.toArray(Predicate[]::new));
        };
    }

    private Specification<Attendance> attendanceSpec(String q, Map<String, String> filters, boolean emp, Long own, Set<Long> visible) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (StringUtils.hasText(q)) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                p.add(cb.like(cb.lower(cb.coalesce(root.get("comment"), "")), like));
            }
            if (StringUtils.hasText(filters.get("status"))) {
                p.add(cb.equal(root.get("status"), filters.get("status")));
            }
            if (StringUtils.hasText(filters.get("date"))) {
                p.add(cb.equal(root.get("date"), LocalDate.parse(filters.get("date").substring(0, 10))));
            }
            if (emp && own != null) {
                p.add(cb.equal(root.get("employeeId"), own));
            } else if (visible != null) {
                p.add(root.get("employeeId").in(visible));
            }
            Long empId = parseLong(filters.get("employeeId"));
            if (empId != null) {
                p.add(cb.equal(root.get("employeeId"), empId));
            }
            return p.isEmpty() ? cb.conjunction() : cb.and(p.toArray(Predicate[]::new));
        };
    }

    private void guardDecision(UserPrincipal actor, Map<String, Object> body, boolean creating, Long ownerId) {
        boolean approver = permissionService.has(actor, PermissionCodes.EXPENSES_APPROVE);
        if (creating || !approver) {
            if (!approver) {
                if (creating) {
                    body.put("status", "PENDING");
                } else {
                    body.remove("status");
                }
            }
            return;
        }
        String status = JsonMaps.str(body, "status");
        if (status == null || "PENDING".equalsIgnoreCase(status)) {
            return;
        }
        if (actor.getEmployeeId() != null && actor.getEmployeeId().equals(ownerId)
                && actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.SUPER_ADMIN) {
            throw BusinessException.forbidden("Vous ne pouvez pas décider votre propre demande");
        }
        if ("REIMBURSED".equalsIgnoreCase(status)
                && actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.SUPER_ADMIN) {
            throw BusinessException.forbidden("Seul un administrateur marque le remboursement");
        }
    }

    private void notifyDecision(Long employeeId, String before, String after, String title, String link) {
        if (after == null || after.equals(before) || "PENDING".equals(after)) {
            return;
        }
        String message = "APPROVED".equals(after) ? title + " approuvé."
                : "REJECTED".equals(after) ? title + " refusé."
                : "REIMBURSED".equals(after) ? title + " remboursé."
                : title + " : " + after;
        notificationService.notifyEmployee(employeeId, title, message, link);
        notificationService.emailEmployee(employeeId, title, message, link, "Ouvrir");
    }

    private static final List<String> ONBOARDING_STEPS = List.of(
            "Déposer la pièce d’identité",
            "Signer et archiver le contrat",
            "Affiliation CNSS",
            "Remettre les accès et le matériel");

    private static final List<String> OFFBOARDING_STEPS = List.of(
            "Restituer le matériel",
            "Clôturer les accès",
            "Établir le solde de tout compte",
            "Remettre l’attestation de travail");

    @Transactional
    public Map<String, Object> launchChecklist(String kind, Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
        boolean onboarding = "onboarding".equals(kind);
        Long employeeId = JsonMaps.lng(body, "employeeId");
        assertChecklist(employeeId, "parcours", "PENDING", actor);
        long existing = onboarding
                ? onboardingTaskRepository.countByEmployeeId(employeeId)
                : offboardingTaskRepository.countByEmployeeId(employeeId);
        if (existing > 0) {
            throw BusinessException.badRequest(onboarding
                    ? "Un parcours d’intégration existe déjà pour cet employé"
                    : "Un parcours de départ existe déjà pour cet employé");
        }
        int created = seedChecklist(TenantContext.requireTenantId(), employeeId, LocalDate.now().plusDays(onboarding ? 14 : 15), onboarding);
        String title = onboarding ? "Intégration" : "Départ";
        String path = onboarding ? "/portal/onboarding" : "/portal/offboarding";
        notificationService.notifyEmployee(employeeId, title, "Votre parcours est ouvert.", path);
        notificationService.emailEmployee(employeeId, title, "Votre parcours est ouvert.", path, "Voir le parcours");
        auditService.log(actor, "CREATE", kind, employeeId, "launch");
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("employeeId", employeeId);
        dto.put("created", created);
        return dto;
    }

    private int seedChecklist(Long tenantId, Long employeeId, LocalDate due, boolean onboarding) {
        List<String> steps = onboarding ? ONBOARDING_STEPS : OFFBOARDING_STEPS;
        for (String task : steps) {
            if (onboarding) {
                OnboardingTask item = new OnboardingTask();
                item.setTenantId(tenantId);
                item.setEmployeeId(employeeId);
                item.setTask(task);
                item.setDueDate(due);
                item.setStatus("PENDING");
                onboardingTaskRepository.save(item);
            } else {
                OffboardingTask item = new OffboardingTask();
                item.setTenantId(tenantId);
                item.setEmployeeId(employeeId);
                item.setTask(task);
                item.setDueDate(due);
                item.setStatus("PENDING");
                offboardingTaskRepository.save(item);
            }
        }
        return steps.size();
    }

    private void requirePerformanceRead(UserPrincipal actor) {
        if (actor.getRole() == UserRole.EMPLOYEE) {
            return;
        }
        permissionService.require(actor, PermissionCodes.PERFORMANCE_MANAGE);
    }

    private void assertNotOwnPerformance(UserPrincipal actor, Long employeeId) {
        if (actor.getRole() == UserRole.ADMIN || actor.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }
        if (actor.getEmployeeId() != null && actor.getEmployeeId().equals(employeeId)) {
            throw BusinessException.forbidden("Vous ne pouvez pas évaluer votre propre dossier");
        }
    }

    private void assertReview(PerformanceReview r, UserPrincipal actor) {
        if (r.getEmployeeId() == null) {
            throw BusinessException.badRequest("Employé obligatoire");
        }
        scopeService.assertEmployeeVisible(actor, r.getEmployeeId());
        assertNotOwnPerformance(actor, r.getEmployeeId());
        if (employeeRepository.findById(r.getEmployeeId()).isEmpty()) {
            throw BusinessException.badRequest("Employé introuvable");
        }
        if (!StringUtils.hasText(r.getPeriod())) {
            throw BusinessException.badRequest("La période est obligatoire");
        }
        if (r.getStatus() != null && !"DRAFT".equals(r.getStatus()) && !"SHARED".equals(r.getStatus()) && !"ACKNOWLEDGED".equals(r.getStatus())) {
            throw BusinessException.badRequest("Statut invalide");
        }
        if (r.getRating() != null && (r.getRating().compareTo(BigDecimal.ZERO) < 0 || r.getRating().compareTo(new BigDecimal("5")) > 0)) {
            throw BusinessException.badRequest("La note doit être entre 0 et 5");
        }
        if (("SHARED".equals(r.getStatus()) || "ACKNOWLEDGED".equals(r.getStatus())) && r.getRating() == null) {
            throw BusinessException.badRequest("La note est obligatoire pour partager l’entretien");
        }
    }

    private void assertObjective(Objective o, UserPrincipal actor) {
        if (o.getEmployeeId() == null) {
            throw BusinessException.badRequest("Employé obligatoire");
        }
        scopeService.assertEmployeeVisible(actor, o.getEmployeeId());
        assertNotOwnPerformance(actor, o.getEmployeeId());
        if (employeeRepository.findById(o.getEmployeeId()).isEmpty()) {
            throw BusinessException.badRequest("Employé introuvable");
        }
        if (!StringUtils.hasText(o.getTitle())) {
            throw BusinessException.badRequest("Le titre est obligatoire");
        }
        int progress = o.getProgress() == null ? 0 : o.getProgress();
        if (progress < 0 || progress > 100) {
            throw BusinessException.badRequest("L’avancement doit être entre 0 et 100");
        }
        o.setProgress(progress);
    }

    private void acknowledgeReview(UserPrincipal actor, PerformanceReview r, Map<String, Object> body) {
        if (actor.getEmployeeId() == null || !actor.getEmployeeId().equals(r.getEmployeeId())) {
            throw BusinessException.forbidden("Accès employé refusé");
        }
        if (!"ACKNOWLEDGED".equals(JsonMaps.str(body, "status"))) {
            throw BusinessException.badRequest("Vous pouvez seulement accuser réception");
        }
        if (!"SHARED".equals(r.getStatus())) {
            throw BusinessException.badRequest("L’entretien n’est pas encore partagé");
        }
        r.setStatus("ACKNOWLEDGED");
    }

    private void updateOwnProgress(UserPrincipal actor, Objective o, Map<String, Object> body) {
        if (actor.getEmployeeId() == null || !actor.getEmployeeId().equals(o.getEmployeeId())) {
            throw BusinessException.forbidden("Accès employé refusé");
        }
        Integer progress = JsonMaps.integer(body, "progress");
        if (progress == null || progress < 0 || progress > 100) {
            throw BusinessException.badRequest("L’avancement doit être entre 0 et 100");
        }
        o.setProgress(progress);
    }

    private void assertJob(JobOffer offer) {
        if (!StringUtils.hasText(offer.getTitle())) {
            throw BusinessException.badRequest("Le titre est obligatoire");
        }
        if (offer.getStatus() != null && !"OPEN".equals(offer.getStatus()) && !"CLOSED".equals(offer.getStatus())) {
            throw BusinessException.badRequest("Statut invalide");
        }
        if (offer.getDepartmentId() != null && departmentRepository.findById(offer.getDepartmentId()).isEmpty()) {
            throw BusinessException.badRequest("Département introuvable");
        }
    }

    private void assertCandidate(Candidate candidate, String previousStatus) {
        if (!StringUtils.hasText(candidate.getFirstName()) || !StringUtils.hasText(candidate.getLastName())) {
            throw BusinessException.badRequest("Le prénom et le nom sont obligatoires");
        }
        if (StringUtils.hasText(candidate.getEmail()) && (!candidate.getEmail().contains("@") || candidate.getEmail().contains(" "))) {
            throw BusinessException.badRequest("E-mail invalide");
        }
        String status = candidate.getStatus();
        if (status != null && !"NEW".equals(status) && !"INTERVIEW".equals(status) && !"HIRED".equals(status) && !"REJECTED".equals(status)) {
            throw BusinessException.badRequest("Statut invalide");
        }
        if (previousStatus == null && ("HIRED".equals(status) || "REJECTED".equals(status))) {
            throw BusinessException.badRequest("Un nouveau candidat commence par Nouveau ou Entretien");
        }
        if (previousStatus != null && !previousStatus.equals(status)) {
            boolean allowed = switch (previousStatus) {
                case "NEW" -> "INTERVIEW".equals(status) || "REJECTED".equals(status);
                case "INTERVIEW" -> "HIRED".equals(status) || "REJECTED".equals(status);
                default -> false;
            };
            if (!allowed) {
                throw BusinessException.badRequest("Ce changement de statut n’est pas permis");
            }
        }
        if (candidate.getJobOfferId() != null) {
            JobOffer offer = jobOfferRepository.findById(candidate.getJobOfferId())
                    .orElseThrow(() -> BusinessException.badRequest("Offre introuvable"));
            boolean entering = previousStatus == null || !previousStatus.equals(status);
            if (entering && !"OPEN".equals(offer.getStatus()) && !"REJECTED".equals(status)) {
                throw BusinessException.badRequest("L’offre est fermée");
            }
        }
    }

    private void notifyCandidate(Candidate candidate, String previousStatus) {
        if (previousStatus != null && previousStatus.equals(candidate.getStatus())) {
            return;
        }
        String name = (candidate.getFirstName() + " " + candidate.getLastName()).trim();
        String message = switch (candidate.getStatus() == null ? "NEW" : candidate.getStatus()) {
            case "INTERVIEW" -> name + " est convoqué en entretien.";
            case "HIRED" -> name + " est marqué recruté. Le dossier salarié se crée dans Employés.";
            case "REJECTED" -> name + " est refusé.";
            default -> "Nouveau candidat : " + name;
        };
        notificationService.notifyAdmins("Candidat", message, "/app/candidates");
        notificationService.emailAdmins("Candidat", message, "/app/candidates", "Voir les candidats");
    }

    private void notifyReviewShared(PerformanceReview r) {
        notificationService.notifyEmployee(r.getEmployeeId(), "Entretien", "Votre entretien " + r.getPeriod() + " est disponible.", "/portal/reviews");
        notificationService.emailEmployee(r.getEmployeeId(), "Entretien", "Votre entretien " + r.getPeriod() + " est disponible.", "/portal/reviews", "Voir l’entretien");
    }

    private void requireChecklistRead(UserPrincipal actor) {
        if (actor.getRole() == UserRole.EMPLOYEE) {
            return;
        }
        permissionService.require(actor, PermissionCodes.EMPLOYEES_VIEW);
    }

    private void completeOwnTask(UserPrincipal actor, Long employeeId, Map<String, Object> body) {
        if (actor.getEmployeeId() == null || !actor.getEmployeeId().equals(employeeId)) {
            throw BusinessException.forbidden("Accès employé refusé");
        }
        if (!"DONE".equals(JsonMaps.str(body, "status"))) {
            throw BusinessException.badRequest("Vous pouvez seulement marquer la tâche comme faite");
        }
    }

    private void assertChecklist(Long employeeId, String task, String status, UserPrincipal actor) {
        if (employeeId == null) {
            throw BusinessException.badRequest("Employé obligatoire");
        }
        scopeService.assertEmployeeVisible(actor, employeeId);
        if (!StringUtils.hasText(task)) {
            throw BusinessException.badRequest("La tâche est obligatoire");
        }
        if (status != null && !"PENDING".equals(status) && !"DONE".equals(status)) {
            throw BusinessException.badRequest("Statut invalide");
        }
        if (employeeRepository.findById(employeeId).isEmpty()) {
            throw BusinessException.badRequest("Employé introuvable");
        }
    }

    private void notifyChecklistAssigned(Long employeeId, String task, boolean onboarding) {
        String title = onboarding ? "Intégration" : "Départ";
        String path = onboarding ? "/portal/onboarding" : "/portal/offboarding";
        notificationService.notifyEmployee(employeeId, title, "Une tâche vous a été ajoutée : " + task, path);
        notificationService.emailEmployee(employeeId, title, "Une tâche vous a été ajoutée : " + task, path, "Voir le parcours");
    }

    private void notifyChecklistComplete(Long employeeId, boolean onboarding) {
        long total = onboarding
                ? onboardingTaskRepository.countByEmployeeId(employeeId)
                : offboardingTaskRepository.countByEmployeeId(employeeId);
        long pending = onboarding
                ? onboardingTaskRepository.countByEmployeeIdAndStatus(employeeId, "PENDING")
                : offboardingTaskRepository.countByEmployeeIdAndStatus(employeeId, "PENDING");
        if (total == 0 || pending > 0) {
            return;
        }
        String title = onboarding ? "Intégration terminée" : "Départ terminé";
        String path = onboarding ? "/app/onboarding" : "/app/offboarding";
        notificationService.notifyManagerOf(employeeId, title, "Toutes les tâches du parcours sont faites.", path);
        notificationService.notifyAdmins(title, "Toutes les tâches du parcours sont faites.", path);
    }

    private void assertExpense(Expense e) {
        if (e.getEmployeeId() == null) {
            throw BusinessException.badRequest("Employé obligatoire");
        }
        if (e.getAmount() == null || e.getAmount().signum() <= 0) {
            throw BusinessException.badRequest("Le montant doit être supérieur à 0");
        }
        if (e.getDate() == null) {
            throw BusinessException.badRequest("Date obligatoire");
        }
    }

    private void assertTrip(BusinessTrip t) {
        if (t.getEmployeeId() == null) {
            throw BusinessException.badRequest("Employé obligatoire");
        }
        if (!StringUtils.hasText(t.getDestination())) {
            throw BusinessException.badRequest("Destination obligatoire");
        }
        if (t.getStartDate() == null || t.getEndDate() == null) {
            throw BusinessException.badRequest("Dates obligatoires");
        }
        if (t.getEndDate().isBefore(t.getStartDate())) {
            throw BusinessException.badRequest("La fin doit être après le début");
        }
    }

    private void assertCanDeleteClaim(UserPrincipal actor, Long employeeId, String status) {
        permissionService.require(actor, PermissionCodes.EXPENSES_VIEW);
        boolean approver = permissionService.has(actor, PermissionCodes.EXPENSES_APPROVE);
        boolean own = actor.getEmployeeId() != null && actor.getEmployeeId().equals(employeeId);
        if (approver && (actor.getRole() == UserRole.ADMIN || actor.getRole() == UserRole.SUPER_ADMIN || !own)) {
            return;
        }
        if (own && "PENDING".equals(status)) {
            return;
        }
        throw BusinessException.forbidden("Cette demande ne peut pas être supprimée");
    }

    private void lockEmployeeSelf(UserPrincipal actor, Map<String, Object> body, boolean creating) {
        if (actor.getRole() != UserRole.EMPLOYEE || actor.getEmployeeId() == null) {
            return;
        }
        body.put("employeeId", actor.getEmployeeId());
        if (creating) {
            body.put("status", "PENDING");
        } else {
            body.remove("status");
        }
    }

    private Long parseLong(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void applyEmployee(Employee e, Map<String, Object> b) {
        if (b.containsKey("firstName")) e.setFirstName(JsonMaps.str(b, "firstName"));
        if (b.containsKey("lastName")) e.setLastName(JsonMaps.str(b, "lastName"));
        if (b.containsKey("email")) e.setEmail(JsonMaps.str(b, "email"));
        if (b.containsKey("cin")) e.setCin(JsonMaps.str(b, "cin"));
        if (b.containsKey("phone")) e.setPhone(JsonMaps.str(b, "phone"));
        if (b.containsKey("city")) e.setCity(JsonMaps.str(b, "city"));
        if (b.containsKey("hireDate")) e.setHireDate(JsonMaps.date(b, "hireDate"));
        if (b.containsKey("exitDate")) e.setExitDate(JsonMaps.date(b, "exitDate"));
        if (b.containsKey("cnssNumber")) e.setCnssNumber(JsonMaps.str(b, "cnssNumber"));
        if (b.containsKey("cnssRegisteredOn")) e.setCnssRegisteredOn(JsonMaps.date(b, "cnssRegisteredOn"));
        if (b.containsKey("amoNumber")) e.setAmoNumber(JsonMaps.str(b, "amoNumber"));
        if (b.containsKey("insurerName")) e.setInsurerName(JsonMaps.str(b, "insurerName"));
        if (b.containsKey("insurerPolicy")) e.setInsurerPolicy(JsonMaps.str(b, "insurerPolicy"));
        if (b.containsKey("insurerStart")) e.setInsurerStart(JsonMaps.date(b, "insurerStart"));
        if (b.containsKey("rib")) e.setRib(JsonMaps.str(b, "rib"));
        if (b.containsKey("departmentId")) e.setDepartmentId(JsonMaps.lng(b, "departmentId"));
        if (b.containsKey("positionId")) e.setPositionId(JsonMaps.lng(b, "positionId"));
        if (b.containsKey("managerId")) e.setManagerId(JsonMaps.lng(b, "managerId"));
        if (b.containsKey("establishmentId")) e.setEstablishmentId(JsonMaps.lng(b, "establishmentId"));
        if (b.containsKey("bankName")) e.setBankName(JsonMaps.str(b, "bankName"));
        if (b.containsKey("bankBic")) e.setBankBic(JsonMaps.str(b, "bankBic"));
        if (b.containsKey("status")) e.setStatus(JsonMaps.str(b, "status"));
        if (b.containsKey("dependents")) {
            Integer d = JsonMaps.integer(b, "dependents");
            e.setDependents(d == null ? 0 : d);
        }
        if (b.containsKey("birthDate")) e.setBirthDate(JsonMaps.date(b, "birthDate"));
        if (b.containsKey("gender")) e.setGender(JsonMaps.str(b, "gender"));
        if (b.containsKey("nationality")) e.setNationality(JsonMaps.str(b, "nationality"));
        if (b.containsKey("maritalStatus")) e.setMaritalStatus(JsonMaps.str(b, "maritalStatus"));
        if (b.containsKey("childrenCount")) e.setChildrenCount(JsonMaps.integer(b, "childrenCount"));
        if (b.containsKey("address")) e.setAddress(JsonMaps.str(b, "address"));
        if (b.containsKey("personalEmail")) e.setPersonalEmail(JsonMaps.str(b, "personalEmail"));
        if (b.containsKey("emergencyName")) e.setEmergencyName(JsonMaps.str(b, "emergencyName"));
        if (b.containsKey("emergencyRelation")) e.setEmergencyRelation(JsonMaps.str(b, "emergencyRelation"));
        if (b.containsKey("emergencyPhone")) e.setEmergencyPhone(JsonMaps.str(b, "emergencyPhone"));
    }

    private void applyDepartment(Department d, Map<String, Object> b) {
        if (b.containsKey("name")) d.setName(JsonMaps.str(b, "name"));
        if (b.containsKey("description")) d.setDescription(JsonMaps.str(b, "description"));
        if (b.containsKey("managerId")) d.setManagerId(JsonMaps.lng(b, "managerId"));
    }

    private void applyPosition(Position p, Map<String, Object> b) {
        if (b.containsKey("title")) p.setTitle(JsonMaps.str(b, "title"));
        if (b.containsKey("departmentId")) p.setDepartmentId(JsonMaps.lng(b, "departmentId"));
        if (b.containsKey("description")) p.setDescription(JsonMaps.str(b, "description"));
        if (b.containsKey("minSalary")) p.setMinSalary(JsonMaps.dec(b, "minSalary"));
        if (b.containsKey("maxSalary")) p.setMaxSalary(JsonMaps.dec(b, "maxSalary"));
        if (b.containsKey("missions")) p.setMissions(JsonMaps.str(b, "missions"));
        if (b.containsKey("skills")) p.setSkills(JsonMaps.str(b, "skills"));
    }

    private void applyContract(Contract c, Map<String, Object> b) {
        if (b.containsKey("employeeId")) c.setEmployeeId(JsonMaps.lng(b, "employeeId"));
        if (b.containsKey("type")) c.setType(JsonMaps.str(b, "type"));
        if (b.containsKey("startDate")) c.setStartDate(JsonMaps.date(b, "startDate"));
        if (b.containsKey("endDate")) c.setEndDate(JsonMaps.date(b, "endDate"));
        if (b.containsKey("baseSalary")) c.setBaseSalary(JsonMaps.dec(b, "baseSalary"));
        if (b.containsKey("weeklyHours")) c.setWeeklyHours(JsonMaps.dec(b, "weeklyHours"));
        if (b.containsKey("departmentId")) c.setDepartmentId(JsonMaps.lng(b, "departmentId"));
        if (b.containsKey("positionId")) c.setPositionId(JsonMaps.lng(b, "positionId"));
        if (b.containsKey("status")) c.setStatus(JsonMaps.str(b, "status"));
        if (b.containsKey("notes")) c.setNotes(JsonMaps.str(b, "notes"));
        if (b.containsKey("documentId")) c.setDocumentId(JsonMaps.lng(b, "documentId"));
        if (b.containsKey("trialStart")) c.setTrialStart(JsonMaps.date(b, "trialStart"));
        if (b.containsKey("trialEnd")) c.setTrialEnd(JsonMaps.date(b, "trialEnd"));
    }

    private void applyAttendance(Attendance a, Map<String, Object> b) {
        if (b.containsKey("employeeId")) a.setEmployeeId(JsonMaps.lng(b, "employeeId"));
        if (b.containsKey("date")) a.setDate(JsonMaps.date(b, "date"));
        if (b.containsKey("checkIn")) a.setCheckIn(JsonMaps.time(b, "checkIn"));
        if (b.containsKey("checkOut")) a.setCheckOut(JsonMaps.time(b, "checkOut"));
        if (b.containsKey("status")) a.setStatus(JsonMaps.str(b, "status"));
        if (b.containsKey("comment")) a.setComment(JsonMaps.str(b, "comment"));
        if (b.containsKey("latitude") && JsonMaps.dec(b, "latitude") != null) {
            a.setLatitude(JsonMaps.dec(b, "latitude").doubleValue());
        }
        if (b.containsKey("longitude") && JsonMaps.dec(b, "longitude") != null) {
            a.setLongitude(JsonMaps.dec(b, "longitude").doubleValue());
        }
        if (b.containsKey("method")) a.setMethod(JsonMaps.str(b, "method"));
    }

    private void applyWorkingHours(WorkingHours w, Map<String, Object> b) {
        if (b.containsKey("name")) w.setName(JsonMaps.str(b, "name"));
        if (b.containsKey("departmentId")) w.setDepartmentId(JsonMaps.lng(b, "departmentId"));
        if (b.containsKey("employeeId")) w.setEmployeeId(JsonMaps.lng(b, "employeeId"));
        if (b.containsKey("startTime")) w.setStartTime(JsonMaps.time(b, "startTime"));
        if (b.containsKey("endTime")) w.setEndTime(JsonMaps.time(b, "endTime"));
        if (b.containsKey("breakStart")) w.setBreakStart(JsonMaps.time(b, "breakStart"));
        if (b.containsKey("breakEnd")) w.setBreakEnd(JsonMaps.time(b, "breakEnd"));
        if (b.containsKey("weeklyHours")) w.setWeeklyHours(JsonMaps.dec(b, "weeklyHours"));
        if (b.containsKey("workDays")) w.setWorkDays(JsonMaps.str(b, "workDays"));
    }

    private void applyLeaveType(LeaveType t, Map<String, Object> b) {
        if (b.containsKey("name")) t.setName(JsonMaps.str(b, "name"));
        if (b.containsKey("code")) t.setCode(JsonMaps.str(b, "code"));
        if (b.containsKey("paid")) t.setPaid(Boolean.TRUE.equals(JsonMaps.bool(b, "paid")));
        if (b.containsKey("deductsAnnual")) t.setDeductsAnnual(Boolean.TRUE.equals(JsonMaps.bool(b, "deductsAnnual")));
    }

    private void applyBalance(LeaveBalance b, Map<String, Object> m) {
        if (m.containsKey("employeeId")) b.setEmployeeId(JsonMaps.lng(m, "employeeId"));
        if (m.containsKey("leaveTypeId")) b.setLeaveTypeId(JsonMaps.lng(m, "leaveTypeId"));
        if (m.containsKey("accrued")) b.setAccrued(nz(JsonMaps.dec(m, "accrued")));
        if (m.containsKey("used")) b.setUsed(nz(JsonMaps.dec(m, "used")));
        if (m.containsKey("remaining")) b.setRemaining(nz(JsonMaps.dec(m, "remaining")));
    }

    private void applyDocument(HrDocument d, Map<String, Object> b) {
        if (b.containsKey("name")) d.setName(JsonMaps.str(b, "name"));
        if (b.containsKey("employeeId")) d.setEmployeeId(JsonMaps.lng(b, "employeeId"));
        if (b.containsKey("category")) d.setCategory(JsonMaps.str(b, "category"));
        if (b.containsKey("visibleToEmployee")) d.setVisibleToEmployee(Boolean.TRUE.equals(JsonMaps.bool(b, "visibleToEmployee")));
    }

    private void applyRequest(EmployeeRequest r, Map<String, Object> b) {
        if (b.containsKey("employeeId")) r.setEmployeeId(JsonMaps.lng(b, "employeeId"));
        if (b.containsKey("type")) r.setType(JsonMaps.str(b, "type"));
        if (b.containsKey("notes")) r.setNotes(JsonMaps.str(b, "notes"));
        if (b.containsKey("status")) r.setStatus(JsonMaps.str(b, "status"));
    }

    private void applyAnnouncement(Announcement a, Map<String, Object> b) {
        if (b.containsKey("title")) a.setTitle(JsonMaps.str(b, "title"));
        if (b.containsKey("body")) a.setBody(JsonMaps.str(b, "body"));
        if (b.containsKey("target")) a.setTarget(JsonMaps.str(b, "target"));
        if (b.containsKey("departmentId")) a.setDepartmentId(JsonMaps.lng(b, "departmentId"));
    }

    private void applyExpense(Expense e, Map<String, Object> b) {
        if (b.containsKey("employeeId")) e.setEmployeeId(JsonMaps.lng(b, "employeeId"));
        if (b.containsKey("category")) e.setCategory(JsonMaps.str(b, "category"));
        if (b.containsKey("amount")) e.setAmount(JsonMaps.dec(b, "amount"));
        if (b.containsKey("date")) e.setDate(JsonMaps.date(b, "date"));
        if (b.containsKey("notes")) e.setNotes(JsonMaps.str(b, "notes"));
        if (b.containsKey("status")) e.setStatus(JsonMaps.str(b, "status"));
        if (b.containsKey("documentId")) e.setDocumentId(JsonMaps.lng(b, "documentId"));
    }

    private void applyTrip(BusinessTrip t, Map<String, Object> b) {
        if (b.containsKey("employeeId")) t.setEmployeeId(JsonMaps.lng(b, "employeeId"));
        if (b.containsKey("destination")) t.setDestination(JsonMaps.str(b, "destination"));
        if (b.containsKey("startDate")) t.setStartDate(JsonMaps.date(b, "startDate"));
        if (b.containsKey("endDate")) t.setEndDate(JsonMaps.date(b, "endDate"));
        if (b.containsKey("purpose")) t.setPurpose(JsonMaps.str(b, "purpose"));
        if (b.containsKey("status")) t.setStatus(JsonMaps.str(b, "status"));
    }

    private void applyReview(PerformanceReview r, Map<String, Object> b) {
        if (b.containsKey("employeeId")) r.setEmployeeId(JsonMaps.lng(b, "employeeId"));
        if (b.containsKey("period")) r.setPeriod(JsonMaps.str(b, "period"));
        if (b.containsKey("rating")) r.setRating(JsonMaps.dec(b, "rating"));
        if (b.containsKey("comments")) r.setComments(JsonMaps.str(b, "comments"));
        if (b.containsKey("status")) r.setStatus(JsonMaps.str(b, "status"));
    }

    private void applyObjective(Objective o, Map<String, Object> b) {
        if (b.containsKey("employeeId")) o.setEmployeeId(JsonMaps.lng(b, "employeeId"));
        if (b.containsKey("title")) o.setTitle(JsonMaps.str(b, "title"));
        if (b.containsKey("dueDate")) o.setDueDate(JsonMaps.date(b, "dueDate"));
        if (b.containsKey("progress")) o.setProgress(JsonMaps.integer(b, "progress"));
    }

    private void applyJob(JobOffer o, Map<String, Object> b) {
        if (b.containsKey("title")) o.setTitle(JsonMaps.str(b, "title"));
        if (b.containsKey("departmentId")) o.setDepartmentId(JsonMaps.lng(b, "departmentId"));
        if (b.containsKey("city")) o.setCity(JsonMaps.str(b, "city"));
        if (b.containsKey("description")) o.setDescription(JsonMaps.str(b, "description"));
        if (b.containsKey("status")) o.setStatus(JsonMaps.str(b, "status"));
        if (b.containsKey("published")) o.setPublished(Boolean.TRUE.equals(JsonMaps.bool(b, "published")));
    }

    private void applyCandidate(Candidate c, Map<String, Object> b) {
        if (b.containsKey("firstName")) c.setFirstName(JsonMaps.str(b, "firstName"));
        if (b.containsKey("lastName")) c.setLastName(JsonMaps.str(b, "lastName"));
        if (b.containsKey("email")) c.setEmail(JsonMaps.str(b, "email"));
        if (b.containsKey("jobOfferId")) c.setJobOfferId(JsonMaps.lng(b, "jobOfferId"));
        if (b.containsKey("status")) c.setStatus(JsonMaps.str(b, "status"));
        if (b.containsKey("phone")) c.setPhone(JsonMaps.str(b, "phone"));
        if (b.containsKey("cvText")) c.setCvText(JsonMaps.str(b, "cvText"));
    }

    private void applyOnboarding(OnboardingTask t, Map<String, Object> b) {
        if (b.containsKey("employeeId")) t.setEmployeeId(JsonMaps.lng(b, "employeeId"));
        if (b.containsKey("task")) t.setTask(JsonMaps.str(b, "task"));
        if (b.containsKey("dueDate")) t.setDueDate(JsonMaps.date(b, "dueDate"));
        if (b.containsKey("status")) t.setStatus(JsonMaps.str(b, "status"));
    }

    private void applyOffboarding(OffboardingTask t, Map<String, Object> b) {
        if (b.containsKey("employeeId")) t.setEmployeeId(JsonMaps.lng(b, "employeeId"));
        if (b.containsKey("task")) t.setTask(JsonMaps.str(b, "task"));
        if (b.containsKey("dueDate")) t.setDueDate(JsonMaps.date(b, "dueDate"));
        if (b.containsKey("status")) t.setStatus(JsonMaps.str(b, "status"));
    }

    private void applyHoliday(Holiday h, Map<String, Object> b) {
        if (b.containsKey("date")) h.setDate(JsonMaps.date(b, "date"));
        if (b.containsKey("name")) h.setName(JsonMaps.str(b, "name"));
        if (b.containsKey("type")) h.setType(JsonMaps.str(b, "type"));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Employee emp(Long id) {
        return id == null ? null : employeeRepository.findById(id).orElse(null);
    }

    private Department dep(Long id) {
        return id == null ? null : departmentRepository.findById(id).orElse(null);
    }

    private Position pos(Long id) {
        return id == null ? null : positionRepository.findById(id).orElse(null);
    }

    private LeaveType lt(Long id) {
        return id == null ? null : leaveTypeRepository.findById(id).orElse(null);
    }

    private JobOffer jo(Long id) {
        return id == null ? null : jobOfferRepository.findById(id).orElse(null);
    }

    public Map<String, Object> toEmployee(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("firstName", e.getFirstName());
        m.put("lastName", e.getLastName());
        m.put("email", e.getEmail());
        m.put("cin", e.getCin());
        m.put("phone", e.getPhone());
        m.put("city", e.getCity());
        m.put("hireDate", e.getHireDate());
        m.put("exitDate", e.getExitDate());
        m.put("cnssNumber", e.getCnssNumber());
        m.put("cnssRegisteredOn", e.getCnssRegisteredOn());
        m.put("amoNumber", e.getAmoNumber());
        m.put("insurerName", e.getInsurerName());
        m.put("insurerPolicy", e.getInsurerPolicy());
        m.put("insurerStart", e.getInsurerStart());
        m.put("rib", e.getRib());
        m.put("departmentId", e.getDepartmentId());
        m.put("positionId", e.getPositionId());
        m.put("managerId", e.getManagerId());
        m.put("establishmentId", e.getEstablishmentId());
        m.put("bankName", e.getBankName());
        m.put("bankBic", e.getBankBic());
        m.put("status", e.getStatus());
        m.put("dependents", e.getDependents());
        m.put("birthDate", e.getBirthDate());
        m.put("gender", e.getGender());
        m.put("nationality", e.getNationality());
        m.put("maritalStatus", e.getMaritalStatus());
        m.put("childrenCount", e.getChildrenCount());
        m.put("address", e.getAddress());
        m.put("personalEmail", e.getPersonalEmail());
        m.put("emergencyName", e.getEmergencyName());
        m.put("emergencyRelation", e.getEmergencyRelation());
        m.put("emergencyPhone", e.getEmergencyPhone());
        m.put("hasPhoto", e.getPhotoPath() != null && !e.getPhotoPath().isBlank());
        m.put("department", JsonMaps.departmentRef(dep(e.getDepartmentId())));
        m.put("position", JsonMaps.positionRef(pos(e.getPositionId())));
        return m;
    }

    private Map<String, Object> toDepartment(Department d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("description", d.getDescription());
        m.put("managerId", d.getManagerId());
        m.put("head", JsonMaps.employeeRef(emp(d.getManagerId())));
        long count = employeeRepository.findAll().stream()
                .filter(e -> d.getId().equals(e.getDepartmentId()))
                .count();
        m.put("headcount", count);
        m.put("employeeCount", count);
        return m;
    }

    private Map<String, Object> toPosition(Position p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("title", p.getTitle());
        m.put("name", p.getTitle());
        m.put("departmentId", p.getDepartmentId());
        m.put("department", JsonMaps.departmentRef(dep(p.getDepartmentId())));
        m.put("description", p.getDescription());
        m.put("minSalary", p.getMinSalary());
        m.put("maxSalary", p.getMaxSalary());
        m.put("missions", p.getMissions());
        m.put("skills", p.getSkills());
        return m;
    }

    private Map<String, Object> toContract(Contract c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("employeeId", c.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(emp(c.getEmployeeId())));
        m.put("type", c.getType());
        m.put("startDate", c.getStartDate());
        m.put("endDate", c.getEndDate());
        m.put("baseSalary", c.getBaseSalary());
        m.put("weeklyHours", c.getWeeklyHours());
        m.put("departmentId", c.getDepartmentId());
        m.put("positionId", c.getPositionId());
        m.put("status", computedStatus(c));
        m.put("alert", contractAlert(c));
        m.put("notes", c.getNotes());
        m.put("trialStart", c.getTrialStart());
        m.put("trialEnd", c.getTrialEnd());
        m.put("documentId", c.getDocumentId());
        m.put("hasFile", c.getDocumentId() != null);
        return m;
    }

    private Map<String, Object> toAttendance(Attendance a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("employeeId", a.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(emp(a.getEmployeeId())));
        m.put("date", a.getDate());
        m.put("checkIn", a.getCheckIn());
        m.put("checkOut", a.getCheckOut());
        m.put("status", a.getStatus());
        m.put("comment", a.getComment());
        m.put("latitude", a.getLatitude());
        m.put("longitude", a.getLongitude());
        m.put("method", a.getMethod());
        m.put("hours", a.getHours());
        return m;
    }

    private Map<String, Object> toWorkingHours(WorkingHours w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("name", w.getName());
        m.put("departmentId", w.getDepartmentId());
        m.put("employeeId", w.getEmployeeId());
        m.put("startTime", w.getStartTime());
        m.put("endTime", w.getEndTime());
        m.put("breakStart", w.getBreakStart());
        m.put("breakEnd", w.getBreakEnd());
        m.put("weeklyHours", w.getWeeklyHours());
        m.put("workDays", w.getWorkDays());
        return m;
    }

    public Map<String, Object> toLeaveType(LeaveType t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("code", t.getCode());
        m.put("paid", t.isPaid());
        m.put("deductsAnnual", t.isDeductsAnnual());
        return m;
    }

    private Map<String, Object> toLeaveBalance(LeaveBalance b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("employeeId", b.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(emp(b.getEmployeeId())));
        m.put("leaveTypeId", b.getLeaveTypeId());
        LeaveType t = lt(b.getLeaveTypeId());
        m.put("leaveType", JsonMaps.leaveTypeRef(t));
        m.put("type", t == null ? null : t.getName());
        m.put("accrued", b.getAccrued());
        m.put("used", b.getUsed());
        m.put("remaining", b.getRemaining());
        m.put("year", b.getYear());
        return m;
    }

    private Map<String, Object> toDocument(HrDocument d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("originalName", d.getOriginalName());
        m.put("title", d.getName());
        m.put("employeeId", d.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(emp(d.getEmployeeId())));
        m.put("category", d.getCategory());
        m.put("visibleToEmployee", d.isVisibleToEmployee());
        m.put("createdAt", d.getCreatedAt());
        m.put("contractId", d.getContractId());
        m.put("contentType", d.getContentType());
        m.put("sizeBytes", d.getSizeBytes());
        m.put("hasFile", StringUtils.hasText(d.getStoragePath()));
        m.put("signedUrl", fileStorageService.sign(d.getStoragePath(), 15));
        return m;
    }

    private Map<String, Object> toRequest(EmployeeRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("employeeId", r.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(emp(r.getEmployeeId())));
        m.put("type", r.getType());
        m.put("status", r.getStatus());
        m.put("notes", r.getNotes());
        m.put("documentId", r.getDocumentId());
        m.put("approvalStep", r.getApprovalStep());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private Map<String, Object> toAnnouncement(Announcement a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("title", a.getTitle());
        m.put("body", a.getBody());
        m.put("target", a.getTarget());
        m.put("departmentId", a.getDepartmentId());
        m.put("createdAt", a.getCreatedAt());
        m.put("date", a.getCreatedAt());
        return m;
    }

    private Map<String, Object> toNotification(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("title", n.getTitle());
        m.put("message", n.getMessage());
        m.put("link", n.getLink());
        m.put("read", n.isReadFlag());
        m.put("createdAt", n.getCreatedAt());
        return m;
    }

    private Map<String, Object> toExpense(Expense e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("employeeId", e.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(emp(e.getEmployeeId())));
        m.put("category", e.getCategory());
        m.put("amount", e.getAmount());
        m.put("date", e.getDate());
        m.put("status", e.getStatus());
        m.put("notes", e.getNotes());
        m.put("documentId", e.getDocumentId());
        return m;
    }

    private Map<String, Object> toTrip(BusinessTrip t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("employeeId", t.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(emp(t.getEmployeeId())));
        m.put("destination", t.getDestination());
        m.put("startDate", t.getStartDate());
        m.put("endDate", t.getEndDate());
        m.put("status", t.getStatus());
        m.put("purpose", t.getPurpose());
        return m;
    }

    private Map<String, Object> toReview(PerformanceReview r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("employeeId", r.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(emp(r.getEmployeeId())));
        m.put("period", r.getPeriod());
        m.put("rating", r.getRating());
        m.put("status", r.getStatus());
        m.put("comments", r.getComments());
        return m;
    }

    private Map<String, Object> toObjective(Objective o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("employeeId", o.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(emp(o.getEmployeeId())));
        m.put("title", o.getTitle());
        m.put("dueDate", o.getDueDate());
        m.put("progress", o.getProgress());
        return m;
    }

    private Map<String, Object> toJob(JobOffer o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("title", o.getTitle());
        m.put("name", o.getTitle());
        m.put("departmentId", o.getDepartmentId());
        m.put("department", JsonMaps.departmentRef(dep(o.getDepartmentId())));
        m.put("city", o.getCity());
        m.put("description", o.getDescription());
        m.put("status", o.getStatus());
        m.put("published", o.isPublished());
        return m;
    }

    private Map<String, Object> toCandidate(Candidate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("firstName", c.getFirstName());
        m.put("lastName", c.getLastName());
        m.put("email", c.getEmail());
        m.put("phone", c.getPhone());
        m.put("cvText", c.getCvText());
        m.put("jobOfferId", c.getJobOfferId());
        m.put("jobOffer", JsonMaps.jobRef(jo(c.getJobOfferId())));
        m.put("status", c.getStatus());
        return m;
    }

    private Map<String, Object> toOnboarding(OnboardingTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("employeeId", t.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(emp(t.getEmployeeId())));
        m.put("task", t.getTask());
        m.put("title", t.getTask());
        m.put("dueDate", t.getDueDate());
        m.put("status", t.getStatus());
        return m;
    }

    private Map<String, Object> toOffboarding(OffboardingTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("employeeId", t.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(emp(t.getEmployeeId())));
        m.put("task", t.getTask());
        m.put("title", t.getTask());
        m.put("dueDate", t.getDueDate());
        m.put("status", t.getStatus());
        return m;
    }

    private Map<String, Object> toHoliday(Holiday h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId());
        m.put("date", h.getDate());
        m.put("name", h.getName());
        m.put("type", h.getType());
        return m;
    }

    private Map<String, Object> toAudit(AuditLog a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("createdAt", a.getCreatedAt());
        m.put("action", a.getAction());
        m.put("entityType", a.getEntityType());
        m.put("entityId", a.getEntityId());
        m.put("userEmail", a.getUserEmail());
        return m;
    }

    public Map<String, Object> toPayslip(Payslip p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("employeeId", p.getEmployeeId());
        m.put("employee", JsonMaps.employeeRef(emp(p.getEmployeeId())));
        m.put("periodId", p.getPeriodId());
        m.put("year", p.getYear());
        m.put("month", p.getMonth());
        m.put("period", p.getMonth() + "/" + p.getYear());
        m.put("yearMonth", p.getYear() + "-" + String.format("%02d", p.getMonth()));
        m.put("baseSalary", p.getBaseSalary());
        m.put("base", p.getBaseSalary());
        m.put("bonuses", p.getBonuses());
        m.put("deductions", p.getDeductions());
        m.put("gross", p.getGross());
        m.put("cnss", p.getCnss());
        m.put("amo", p.getAmo());
        m.put("ir", p.getIr());
        m.put("net", p.getNet());
        m.put("employerCost", p.getEmployerCost());
        m.put("overtimeAmount", p.getOvertimeAmount());
        m.put("unpaidAbsence", p.getUnpaidAbsence());
        m.put("indemnities", p.getIndemnities() == null ? BigDecimal.ZERO : p.getIndemnities());
        m.put("paidLeaveDays", p.getPaidLeaveDays() == null ? 0 : p.getPaidLeaveDays());
        m.put("linkNote", p.getLinkNote());
        m.put("advances", p.getAdvances());
        m.put("loanDeduction", p.getLoanDeduction());
        m.put("garnishments", p.getGarnishments());
        m.put("creditNote", p.getCreditNote());
        m.put("cimrEmployee", p.getCimrEmployee());
        m.put("mutualEmployee", p.getMutualEmployee());
        m.put("declaredDays", p.getDeclaredDays());
        m.put("unpaidDays", p.getUnpaidDays() == null ? 0 : p.getUnpaidDays());
        m.put("snapshotCnss", p.getSnapshotCnss());
        m.put("snapshotRib", p.getSnapshotRib());
        m.put("emailedAt", p.getEmailedAt());
        m.put("smigAlert", p.isSmigAlert());
        m.put("ruleSetId", p.getRuleSetId());
        m.put("pdfUrl", "/payslips/" + p.getId() + "/pdf");
        m.put("transferStatus", p.getTransferStatus() == null ? "PENDING" : p.getTransferStatus());
        m.put("transferDate", p.getTransferDate());
        m.put("transferReference", p.getTransferReference());
        return m;
    }

    private Map<String, Object> employeeView(Employee employee, UserPrincipal actor) {
        Map<String, Object> dto = toEmployee(employee);
        boolean medical = permissionService.has(actor, PermissionCodes.DOCUMENTS_PRIVATE);
        dto.put("canViewMedical", medical);
        if (medical) {
            dto.put("bloodType", employee.getBloodType());
            dto.put("medicalNotes", employee.getMedicalNotes());
        }
        return dto;
    }

    @Transactional
    public Map<String, Object> generateEmployeeDocument(Long employeeId, Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.DOCUMENTS_UPLOAD);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
        scopeService.assertEmployeeVisible(actor, employee.getId());
        String kind = JsonMaps.str(body, "type");
        if (!StringUtils.hasText(kind)) {
            throw BusinessException.badRequest("Type de document requis");
        }
        kind = kind.trim().toUpperCase(Locale.ROOT);
        if (kind.contains("SALAIRE")) {
            permissionService.require(actor, PermissionCodes.REQUESTS_PROCESS);
        }
        String extra = "N° CNSS : " + nzText(employee.getCnssNumber())
                + " · Immatriculation : " + (employee.getCnssRegisteredOn() == null ? "—" : employee.getCnssRegisteredOn())
                + " · AMO : " + nzText(employee.getAmoNumber())
                + " · Assurance : " + nzText(employee.getInsurerName())
                + " " + nzText(employee.getInsurerPolicy());
        byte[] pdf = pdfDocumentService.attestation(employee, kind, extra);
        StoredFile stored = fileStorageService.storeBytes(pdf, "application/pdf", kind.toLowerCase(Locale.ROOT) + "-" + employee.getId() + ".pdf");
        HrDocument doc = saveFile(employee.getTenantId(), employee.getId(), null, "ATTESTATION", stored, true);
        doc.setName(documentTitle(kind));
        doc = hrDocumentRepository.save(doc);
        auditService.log(actor, "GENERATE", "documents", doc.getId(), kind);
        return toDocument(doc);
    }

    private String documentTitle(String kind) {
        if (kind.contains("SALAIRE")) return "Attestation de salaire";
        if (kind.contains("EMPLOI")) return "Attestation d’emploi";
        return "Attestation de travail";
    }

    private String nzText(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    public Map<String, Object> markNotificationRead(Long id, UserPrincipal actor) {
        return toNotification(notificationService.markRead(id, actor));
    }

    public Map<String, Object> markAllNotificationsRead(UserPrincipal actor) {
        int n = notificationService.markAllRead(actor);
        return Map.of("updated", n);
    }

    private Contract finalizeContract(Contract c) {
        if ("ACTIVE".equalsIgnoreCase(c.getStatus()) && c.getEmployeeId() != null) {
            for (Contract other : contractRepository.findByEmployeeIdAndStatus(c.getEmployeeId(), "ACTIVE")) {
                if (!other.getId().equals(c.getId())) {
                    other.setStatus("TERMINATED");
                    contractRepository.save(other);
                }
            }
        }
        String alert = contractAlert(c);
        if (alert != null && !alert.equals(c.getLastAlertSent())) {
            notificationService.notifyAdmins(
                    "Contrat à échéance",
                    "Contrat " + c.getId() + " : alerte " + alert,
                    "/app/contracts");
            notificationService.emailAdmins(
                    "Contrat à échéance",
                    "Un contrat arrive à échéance (alerte " + alert + ").",
                    "/app/contracts",
                    "Voir les contrats");
            c.setLastAlertSent(alert);
            return contractRepository.save(c);
        }
        return c;
    }

    private String contractAlert(Contract c) {
        if ("TERMINATED".equalsIgnoreCase(c.getStatus()) || "ENDED".equalsIgnoreCase(c.getStatus())) {
            return null;
        }
        LocalDate end = c.getEndDate();
        if (end == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        if (end.isBefore(today)) {
            return "OVERDUE";
        }
        if (!end.isAfter(today.plusDays(7))) {
            return "7";
        }
        if (!end.isAfter(today.plusDays(30))) {
            return "30";
        }
        if (!end.isAfter(today.plusDays(90))) {
            return "90";
        }
        return null;
    }

    private String computedStatus(Contract c) {
        if ("TERMINATED".equalsIgnoreCase(c.getStatus()) || "ENDED".equalsIgnoreCase(c.getStatus())) {
            return "TERMINATED";
        }
        LocalDate end = c.getEndDate();
        if (end == null) {
            return "ACTIVE";
        }
        LocalDate today = LocalDate.now();
        if (end.isBefore(today)) {
            return "EXPIRED";
        }
        if (!end.isAfter(today.plusDays(90))) {
            return "EXPIRING";
        }
        return "ACTIVE";
    }
}
