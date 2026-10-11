package ma.rhflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.Attendance;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.HrRecord;
import ma.rhflow.domain.entity.Payslip;
import ma.rhflow.domain.entity.ProfileChange;
import ma.rhflow.domain.entity.TrainingCourse;
import ma.rhflow.domain.entity.TrainingEnrollment;
import ma.rhflow.domain.enums.PermissionCodes;
import ma.rhflow.domain.repository.AttendanceRepository;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.domain.repository.HrRecordRepository;
import ma.rhflow.domain.repository.PayslipRepository;
import ma.rhflow.domain.repository.ProfileChangeRepository;
import ma.rhflow.domain.repository.TrainingCourseRepository;
import ma.rhflow.domain.repository.TrainingEnrollmentRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.tenant.TenantContext;
import ma.rhflow.util.JsonMaps;

@Service
@RequiredArgsConstructor
public class WorkplaceService {

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");
    private static final Set<String> FIELDS = Set.of(
            "ADDRESS", "CITY", "PHONE", "PERSONAL_EMAIL", "RIB", "MARITAL_STATUS", "DEPENDENTS", "CHILDREN_COUNT");

    private final ProfileChangeRepository profileChangeRepository;
    private final TrainingCourseRepository courseRepository;
    private final TrainingEnrollmentRepository enrollmentRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final HrRecordRepository hrRecordRepository;
    private final PayslipRepository payslipRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final MailService mailService;

    public List<Map<String, Object>> listChanges(UserPrincipal actor) {
        boolean hr = permissionService.has(actor, PermissionCodes.EMPLOYEES_EDIT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProfileChange row : profileChangeRepository.findAll()) {
            if (!hr && (actor.getEmployeeId() == null || !actor.getEmployeeId().equals(row.getEmployeeId()))) {
                continue;
            }
            out.add(toChange(row));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> propose(Map<String, Object> body, UserPrincipal actor) {
        Long employeeId = actor.getEmployeeId();
        if (permissionService.has(actor, PermissionCodes.EMPLOYEES_EDIT) && JsonMaps.lng(body, "employeeId") != null) {
            employeeId = JsonMaps.lng(body, "employeeId");
        }
        if (employeeId == null) {
            throw BusinessException.badRequest("Aucun salarié lié à ce compte");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
        String field = JsonMaps.str(body, "field");
        if (field == null) {
            throw BusinessException.badRequest("Champ requis");
        }
        field = field.trim().toUpperCase(Locale.ROOT);
        if (!FIELDS.contains(field)) {
            throw BusinessException.badRequest("Champ non modifiable par le salarié");
        }
        String proposed = JsonMaps.str(body, "value");
        if (proposed == null || proposed.isBlank()) {
            throw BusinessException.badRequest("Nouvelle valeur requise");
        }
        proposed = proposed.trim();
        if ("RIB".equals(field)) {
            String digits = proposed.replaceAll("\\s+", "");
            if (!digits.matches("\\d{24}")) {
                throw BusinessException.badRequest("Le RIB doit contenir 24 chiffres");
            }
            proposed = digits;
        }
        if ("DEPENDENTS".equals(field) || "CHILDREN_COUNT".equals(field)) {
            try {
                int n = Integer.parseInt(proposed);
                if (n < 0 || n > 20) {
                    throw BusinessException.badRequest("Nombre hors limite");
                }
                proposed = String.valueOf(n);
            } catch (NumberFormatException ex) {
                throw BusinessException.badRequest("Nombre entier requis");
            }
        }
        if (!profileChangeRepository.findByEmployeeIdAndFieldNameAndStatus(employeeId, field, "PENDING").isEmpty()) {
            throw BusinessException.conflict("Une demande est déjà en attente pour ce champ");
        }
        ProfileChange change = new ProfileChange();
        change.setTenantId(TenantContext.requireTenantId());
        change.setEmployeeId(employeeId);
        change.setFieldName(field);
        change.setCurrentValue(current(employee, field));
        change.setProposedValue(proposed);
        change.setStatus("PENDING");
        profileChangeRepository.save(change);
        auditService.log(actor, "PROPOSE", "profile-change", change.getId(), field);
        return toChange(change);
    }

    @Transactional
    public Map<String, Object> decide(Long id, Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
        ProfileChange change = profileChangeRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Demande introuvable"));
        if (!"PENDING".equals(change.getStatus())) {
            throw BusinessException.badRequest("Demande déjà traitée");
        }
        String status = JsonMaps.str(body, "status");
        if (status == null) {
            throw BusinessException.badRequest("Décision requise");
        }
        status = status.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw BusinessException.badRequest("Décision APPROVED ou REJECTED");
        }
        change.setStatus(status);
        change.setDecisionComment(JsonMaps.str(body, "comment"));
        change.setDecidedBy(actor.getId());
        if ("APPROVED".equals(status)) {
            Employee employee = employeeRepository.findById(change.getEmployeeId())
                    .orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
            apply(employee, change.getFieldName(), change.getProposedValue());
            employeeRepository.save(employee);
            if (employee.getEmail() != null && !employee.getEmail().isBlank()) {
                mailService.send(
                        employee.getEmail(),
                        "Fiche mise à jour",
                        "Votre demande de modification (" + label(change.getFieldName()) + ") est acceptée.",
                        "/portal/profile",
                        "Voir ma fiche");
            }
        }
        profileChangeRepository.save(change);
        auditService.log(actor, status, "profile-change", id, change.getFieldName());
        return toChange(change);
    }

    public Map<String, Object> training(int year, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.EMPLOYEES_VIEW);
        List<Map<String, Object>> courses = new ArrayList<>();
        for (TrainingCourse course : courseRepository.findAll()) {
            if (course.getYear() != null && course.getYear() != year) {
                continue;
            }
            courses.add(toCourse(course));
        }
        List<Map<String, Object>> enrollments = new ArrayList<>();
        BigDecimal spend = BigDecimal.ZERO;
        for (TrainingEnrollment enrollment : enrollmentRepository.findAll()) {
            TrainingCourse course = enrollment.getCourseId() == null
                    ? null
                    : courseRepository.findById(enrollment.getCourseId()).orElse(null);
            if (course != null && course.getYear() != null && course.getYear() != year) {
                continue;
            }
            enrollments.add(toEnrollment(enrollment, course));
            if ("DONE".equals(enrollment.getStatus()) && course != null && course.getCost() != null) {
                spend = spend.add(course.getCost());
            }
        }
        BigDecimal tfp = BigDecimal.ZERO;
        if (permissionService.has(actor, PermissionCodes.PAYROLL_VIEW)) {
            for (Payslip slip : payslipRepository.findByYear(year)) {
                if (slip.getEmployerTfp() != null) {
                    tfp = tfp.add(slip.getEmployerTfp());
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("courses", courses);
        out.put("enrollments", enrollments);
        out.put("trainingSpend", spend);
        out.put("tfpCollected", tfp);
        out.put("tfpGap", tfp.subtract(spend));
        return out;
    }

    @Transactional
    public Map<String, Object> createCourse(Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
        String title = JsonMaps.str(body, "title");
        if (title == null || title.isBlank()) {
            throw BusinessException.badRequest("Intitulé requis");
        }
        TrainingCourse course = new TrainingCourse();
        course.setTenantId(TenantContext.requireTenantId());
        course.setTitle(title.trim());
        course.setProvider(JsonMaps.str(body, "provider"));
        course.setHours(JsonMaps.dec(body, "hours") == null ? BigDecimal.ZERO : JsonMaps.dec(body, "hours"));
        course.setCost(JsonMaps.dec(body, "cost") == null ? BigDecimal.ZERO : JsonMaps.dec(body, "cost"));
        Integer year = JsonMaps.integer(body, "year");
        course.setYear(year == null ? LocalDate.now(CASABLANCA).getYear() : year);
        courseRepository.save(course);
        auditService.log(actor, "CREATE", "training-course", course.getId(), course.getTitle());
        return toCourse(course);
    }

    @Transactional
    public Map<String, Object> enroll(Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
        Long courseId = JsonMaps.lng(body, "courseId");
        Long employeeId = JsonMaps.lng(body, "employeeId");
        if (courseId == null || employeeId == null) {
            throw BusinessException.badRequest("Formation et salarié requis");
        }
        TrainingCourse course = courseRepository.findById(courseId)
                .orElseThrow(() -> BusinessException.notFound("Formation introuvable"));
        employeeRepository.findById(employeeId).orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
        TrainingEnrollment enrollment = new TrainingEnrollment();
        enrollment.setTenantId(TenantContext.requireTenantId());
        enrollment.setCourseId(courseId);
        enrollment.setEmployeeId(employeeId);
        enrollment.setStatus("PLANNED");
        enrollmentRepository.save(enrollment);
        auditService.log(actor, "ENROLL", "training", enrollment.getId(), course.getTitle());
        return toEnrollment(enrollment, course);
    }

    @Transactional
    public Map<String, Object> completeEnrollment(Long id, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
        TrainingEnrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Inscription introuvable"));
        enrollment.setStatus("DONE");
        enrollment.setCompletedOn(LocalDate.now(CASABLANCA));
        enrollmentRepository.save(enrollment);
        TrainingCourse course = enrollment.getCourseId() == null
                ? null
                : courseRepository.findById(enrollment.getCourseId()).orElse(null);
        auditService.log(actor, "DONE", "training", id, null);
        return toEnrollment(enrollment, course);
    }

    @Transactional
    public Map<String, Object> clock(UserPrincipal actor) {
        return clock(Map.of(), actor);
    }

    @Transactional
    public Map<String, Object> clock(Map<String, Object> body, UserPrincipal actor) {
        if (actor.getEmployeeId() == null) {
            throw BusinessException.badRequest("Ce compte n’est pas lié à un salarié");
        }
        LocalDate today = LocalDate.now(CASABLANCA);
        LocalTime now = LocalTime.now(CASABLANCA).withNano(0);
        Employee employee = employeeRepository.findById(actor.getEmployeeId())
                .orElseThrow(() -> BusinessException.badRequest("Ce compte n’est pas lié à un salarié"));
        Double lat = JsonMaps.dec(body, "latitude") == null ? null : JsonMaps.dec(body, "latitude").doubleValue();
        Double lng = JsonMaps.dec(body, "longitude") == null ? null : JsonMaps.dec(body, "longitude").doubleValue();
        String qr = JsonMaps.str(body, "qrToken", "qr");
        String method = "MANUAL";
        if (qr != null && !qr.isBlank()) {
            method = "QR";
        } else if (lat != null && lng != null) {
            method = "GPS";
            assertGeo(employee, lat, lng);
        }
        Attendance row = attendanceRepository.findByEmployeeIdAndDate(actor.getEmployeeId(), today).orElseGet(() -> {
            Attendance created = new Attendance();
            created.setTenantId(TenantContext.requireTenantId());
            created.setEmployeeId(actor.getEmployeeId());
            created.setDate(today);
            return created;
        });
        String action;
        if (row.getCheckIn() == null) {
            row.setCheckIn(now);
            row.setStatus("PRESENT");
            action = "ARRIVEE";
        } else {
            row.setCheckOut(now);
            row.setStatus("PRESENT");
            action = "DEPART";
        }
        row.setLatitude(lat);
        row.setLongitude(lng);
        row.setMethod(method);
        attendanceRepository.save(row);
        auditService.log(actor, action, "attendance", row.getId(), method);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", action);
        out.put("date", today);
        out.put("checkIn", row.getCheckIn());
        out.put("checkOut", row.getCheckOut());
        out.put("status", row.getStatus());
        out.put("method", method);
        return out;
    }

    private void assertGeo(Employee employee, double lat, double lng) {
        List<HrRecord> sites = hrRecordRepository.findByKindOrderByIdDesc(HrRecord.ESTABLISHMENT);
        if (sites.isEmpty()) {
            return;
        }
        for (HrRecord site : sites) {
            if (employee.getEstablishmentId() != null && !employee.getEstablishmentId().equals(site.getId())) {
                continue;
            }
            if (site.getLatitude() == null || site.getLongitude() == null) {
                continue;
            }
            double radius = site.getRadiusMeters() == null || site.getRadiusMeters() <= 0 ? 250 : site.getRadiusMeters();
            if (haversine(lat, lng, site.getLatitude(), site.getLongitude()) <= radius) {
                return;
            }
        }
        throw BusinessException.badRequest("Vous êtes hors de la zone de pointage de l’établissement");
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private void apply(Employee employee, String field, String value) {
        switch (field) {
            case "ADDRESS" -> employee.setAddress(value);
            case "CITY" -> employee.setCity(value);
            case "PHONE" -> employee.setPhone(value);
            case "PERSONAL_EMAIL" -> employee.setPersonalEmail(value);
            case "RIB" -> employee.setRib(value);
            case "MARITAL_STATUS" -> employee.setMaritalStatus(value);
            case "DEPENDENTS" -> employee.setDependents(Integer.parseInt(value));
            case "CHILDREN_COUNT" -> employee.setChildrenCount(Integer.parseInt(value));
            default -> throw BusinessException.badRequest("Champ non modifiable");
        }
    }

    private String current(Employee employee, String field) {
        return switch (field) {
            case "ADDRESS" -> employee.getAddress();
            case "CITY" -> employee.getCity();
            case "PHONE" -> employee.getPhone();
            case "PERSONAL_EMAIL" -> employee.getPersonalEmail();
            case "RIB" -> employee.getRib();
            case "MARITAL_STATUS" -> employee.getMaritalStatus();
            case "DEPENDENTS" -> employee.getDependents() == null ? "0" : String.valueOf(employee.getDependents());
            case "CHILDREN_COUNT" -> employee.getChildrenCount() == null ? "0" : String.valueOf(employee.getChildrenCount());
            default -> "";
        };
    }

    private String label(String field) {
        return switch (field) {
            case "ADDRESS" -> "adresse";
            case "CITY" -> "ville";
            case "PHONE" -> "téléphone";
            case "PERSONAL_EMAIL" -> "e-mail personnel";
            case "RIB" -> "RIB";
            case "MARITAL_STATUS" -> "situation familiale";
            case "DEPENDENTS" -> "personnes à charge";
            case "CHILDREN_COUNT" -> "enfants";
            default -> field;
        };
    }

    private Map<String, Object> toChange(ProfileChange change) {
        Employee employee = employeeRepository.findById(change.getEmployeeId()).orElse(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", change.getId());
        row.put("employeeId", change.getEmployeeId());
        row.put("employee", JsonMaps.employeeRef(employee));
        row.put("field", change.getFieldName());
        row.put("fieldLabel", label(change.getFieldName()));
        row.put("currentValue", change.getCurrentValue());
        row.put("proposedValue", change.getProposedValue());
        row.put("status", change.getStatus());
        row.put("decisionComment", change.getDecisionComment());
        row.put("createdAt", change.getCreatedAt());
        return row;
    }

    private Map<String, Object> toCourse(TrainingCourse course) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", course.getId());
        row.put("title", course.getTitle());
        row.put("provider", course.getProvider());
        row.put("hours", course.getHours());
        row.put("cost", course.getCost());
        row.put("year", course.getYear());
        return row;
    }

    private Map<String, Object> toEnrollment(TrainingEnrollment enrollment, TrainingCourse course) {
        Employee employee = employeeRepository.findById(enrollment.getEmployeeId()).orElse(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", enrollment.getId());
        row.put("courseId", enrollment.getCourseId());
        row.put("courseTitle", course == null ? "" : course.getTitle());
        row.put("employeeId", enrollment.getEmployeeId());
        row.put("employee", JsonMaps.employeeRef(employee));
        row.put("status", enrollment.getStatus());
        row.put("completedOn", enrollment.getCompletedOn());
        row.put("cost", course == null ? BigDecimal.ZERO : course.getCost());
        return row;
    }
}
