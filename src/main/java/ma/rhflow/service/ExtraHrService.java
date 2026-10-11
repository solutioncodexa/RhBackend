package ma.rhflow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.minio.MinioClient;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.Candidate;
import ma.rhflow.domain.entity.Contract;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.HrDocument;
import ma.rhflow.domain.entity.HrRecord;
import ma.rhflow.domain.entity.JobOffer;
import ma.rhflow.domain.entity.Payslip;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.enums.PermissionCodes;
import ma.rhflow.domain.repository.CandidateRepository;
import ma.rhflow.domain.repository.ContractRepository;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.domain.repository.HrDocumentRepository;
import ma.rhflow.domain.repository.HrRecordRepository;
import ma.rhflow.domain.repository.JobOfferRepository;
import ma.rhflow.domain.repository.PayslipRepository;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.tenant.TenantContext;
import ma.rhflow.util.JsonMaps;

@Service
public class ExtraHrService {

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

    private final HrRecordRepository records;
    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;
    private final PayslipRepository payslipRepository;
    private final HrDocumentRepository documentRepository;
    private final JobOfferRepository jobOfferRepository;
    private final CandidateRepository candidateRepository;
    private final TenantRepository tenantRepository;
    private final PayrollEngine engine;
    private final PermissionService permissionService;
    private final ScopeService scopeService;
    private final AuditService auditService;
    private final ObjectProvider<MinioClient> minio;
    private final ObjectProvider<RedisConnectionFactory> redis;
    private final CacheManager cacheManager;
    private final boolean mailEnabled;
    private final boolean minioEnabled;
    private final boolean redisEnabled;
    private final boolean whatsappEnabled;

    public ExtraHrService(
            HrRecordRepository records,
            EmployeeRepository employeeRepository,
            ContractRepository contractRepository,
            PayslipRepository payslipRepository,
            HrDocumentRepository documentRepository,
            JobOfferRepository jobOfferRepository,
            CandidateRepository candidateRepository,
            TenantRepository tenantRepository,
            PayrollEngine engine,
            PermissionService permissionService,
            ScopeService scopeService,
            AuditService auditService,
            ObjectProvider<MinioClient> minio,
            ObjectProvider<RedisConnectionFactory> redis,
            CacheManager cacheManager,
            @Value("${app.mail.enabled:true}") boolean mailEnabled,
            @Value("${app.minio.enabled:true}") boolean minioEnabled,
            @Value("${app.redis.enabled:true}") boolean redisEnabled,
            @Value("${app.whatsapp.enabled:true}") boolean whatsappEnabled) {
        this.records = records;
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.payslipRepository = payslipRepository;
        this.documentRepository = documentRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.candidateRepository = candidateRepository;
        this.tenantRepository = tenantRepository;
        this.engine = engine;
        this.permissionService = permissionService;
        this.scopeService = scopeService;
        this.auditService = auditService;
        this.minio = minio;
        this.redis = redis;
        this.cacheManager = cacheManager;
        this.mailEnabled = mailEnabled;
        this.minioEnabled = minioEnabled;
        this.redisEnabled = redisEnabled;
        this.whatsappEnabled = whatsappEnabled;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String kind, UserPrincipal actor) {
        requireKind(actor, kind, false);
        try {
            Set<Long> visible = scopeService.visibleEmployeeIds(actor);
            return records.findByKindOrderByIdDesc(kind).stream()
                    .filter(row -> visible == null || row.getEmployeeId() == null || visible.contains(row.getEmployeeId()))
                    .map(this::toDto)
                    .toList();
        } catch (RuntimeException ex) {
            throw ma.rhflow.common.exception.BusinessException.badRequest(
                    "Liste indisponible (" + ex.getClass().getSimpleName() + ") : " + ex.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> create(String kind, Map<String, Object> body, UserPrincipal actor) {
        requireKind(actor, kind, true);
        HrRecord row = new HrRecord();
        row.setTenantId(TenantContext.requireTenantId());
        row.setKind(kind);
        apply(row, body);
        if (row.getEmployeeId() != null) {
            scopeService.assertEmployeeVisible(actor, row.getEmployeeId());
        }
        if (HrRecord.ESTABLISHMENT.equals(kind) && (row.getCode() == null || row.getCode().isBlank())) {
            row.setCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (HrRecord.LOAN.equals(kind)) {
            if (row.getStatus() == null) {
                row.setStatus("ACTIVE");
            }
            if (row.getRemaining() == null || row.getRemaining().signum() == 0) {
                row.setRemaining(n(row.getAmount()));
            }
        }
        if (HrRecord.ASSET.equals(kind) && row.getStatus() == null) {
            row.setStatus("ASSIGNED");
        }
        if (HrRecord.DISCIPLINARY.equals(kind) && row.getStatus() == null) {
            row.setStatus("OPEN");
        }
        records.save(row);
        auditService.log(actor, "CREATE", kind.toLowerCase(), row.getId(), row.getTitle());
        return toDto(row);
    }

    @Transactional
    public Map<String, Object> update(String kind, Long id, Map<String, Object> body, UserPrincipal actor) {
        requireKind(actor, kind, true);
        HrRecord row = records.findById(id).orElseThrow(() -> BusinessException.notFound("Enregistrement introuvable"));
        if (!kind.equals(row.getKind())) {
            throw BusinessException.notFound("Enregistrement introuvable");
        }
        apply(row, body);
        records.save(row);
        return toDto(row);
    }

    @Transactional
    public void delete(String kind, Long id, UserPrincipal actor) {
        requireKind(actor, kind, true);
        HrRecord row = records.findById(id).orElseThrow(() -> BusinessException.notFound("Enregistrement introuvable"));
        records.delete(row);
    }

    @Transactional
    public Map<String, Object> signDocument(Long documentId, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.DOCUMENTS_VIEW);
        HrDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> BusinessException.notFound("Document introuvable"));
        String category = doc.getCategory() == null ? "" : doc.getCategory().toUpperCase();
        boolean employee = actor.getRole() == ma.rhflow.domain.enums.UserRole.EMPLOYEE;
        if (employee) {
            if (actor.getEmployeeId() == null || !actor.getEmployeeId().equals(doc.getEmployeeId())) {
                throw BusinessException.forbidden("Vous ne pouvez signer que vos documents");
            }
            if (!"CONTRACT".equals(category) && !"ADMIN".equals(category)) {
                throw BusinessException.badRequest("Une attestation, un bulletin ou une pièce se consulte. Le salarié accuse réception d’un contrat ou d’un document administratif.");
            }
        } else if (!permissionService.has(actor, PermissionCodes.DOCUMENTS_UPLOAD)) {
            throw BusinessException.forbidden("Seul le service RH signe ce document");
        }
        HrRecord sig = new HrRecord();
        sig.setTenantId(TenantContext.requireTenantId());
        sig.setKind(HrRecord.SIGNATURE);
        sig.setRelatedId(documentId);
        sig.setEmployeeId(actor.getEmployeeId() == null ? doc.getEmployeeId() : actor.getEmployeeId());
        sig.setTitle(doc.getName());
        sig.setStatus("SIGNED");
        sig.setStartDate(LocalDate.now(CASABLANCA));
        sig.setPayload(hash(actor.getEmail() + "|" + documentId + "|" + Instant.now()));
        records.save(sig);
        auditService.log(actor, "SIGN", "document", documentId, sig.getPayload());
        return toDto(sig);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> simulate(Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
        BigDecimal targetNet = n(JsonMaps.dec(body, "targetNet", "net"));
        if (targetNet.signum() <= 0) {
            throw BusinessException.badRequest("Indiquez un net souhaité");
        }
        Employee employee = sample(JsonMaps.lng(body, "employeeId"));
        Contract contract = new Contract();
        contract.setWeeklyHours(new BigDecimal("44"));
        BigDecimal low = targetNet;
        BigDecimal high = targetNet.multiply(new BigDecimal("2.4")).add(new BigDecimal("2000"));
        Payslip best = null;
        for (int i = 0; i < 28; i++) {
            BigDecimal mid = low.add(high).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            contract.setBaseSalary(mid);
            Payslip slip = engine.calculate(employee, contract, LocalDate.now().getYear(), LocalDate.now().getMonthValue());
            best = slip;
            if (n(slip.getNet()).compareTo(targetNet) >= 0) {
                high = mid;
            } else {
                low = mid;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("targetNet", targetNet);
        out.put("suggestedGross", best == null ? null : best.getGross());
        out.put("suggestedBase", contract.getBaseSalary());
        out.put("cnss", best == null ? null : best.getCnss());
        out.put("amo", best == null ? null : best.getAmo());
        out.put("ir", best == null ? null : best.getIr());
        out.put("net", best == null ? null : best.getNet());
        out.put("employerCost", best == null ? null : best.getEmployerCost());
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> irRegularize(int year, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
        Map<Long, BigDecimal> paid = new LinkedHashMap<>();
        Map<Long, BigDecimal> taxableProxy = new LinkedHashMap<>();
        for (Payslip p : payslipRepository.findByYear(year)) {
            paid.merge(p.getEmployeeId(), n(p.getIr()), BigDecimal::add);
            taxableProxy.merge(p.getEmployeeId(), n(p.getGross()).subtract(n(p.getCnss())).subtract(n(p.getAmo())), BigDecimal::add);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long employeeId : paid.keySet()) {
            Employee e = employeeRepository.findById(employeeId).orElse(null);
            if (e == null) {
                continue;
            }
            BigDecimal annualTaxable = n(taxableProxy.get(employeeId));
            BigDecimal due = engine.irAnnual(annualTaxable);
            int deps = e.getDependents() == null ? 0 : e.getDependents();
            due = due.subtract(new BigDecimal("360").multiply(BigDecimal.valueOf(Math.min(deps, 6)))).max(BigDecimal.ZERO);
            BigDecimal already = n(paid.get(employeeId));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", employeeId);
            row.put("employee", e.getLastName() + " " + e.getFirstName());
            row.put("irPaid", already);
            row.put("irDue", due);
            row.put("adjustment", due.subtract(already));
            rows.add(row);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> settlement(Long employeeId, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
        Employee e = employeeRepository.findById(employeeId)
                .orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
        Contract contract = contractRepository.findByEmployeeIdAndStatus(employeeId, "ACTIVE").stream().findFirst()
                .orElseThrow(() -> BusinessException.badRequest("Aucun contrat actif"));
        LocalDate today = LocalDate.now(CASABLANCA);
        Payslip last = engine.calculate(e, contract, today.getYear(), today.getMonthValue());
        long years = e.getHireDate() == null ? 0 : java.time.Period.between(e.getHireDate(), today).getYears();
        BigDecimal daily = n(contract.getBaseSalary()).divide(new BigDecimal("26"), 2, RoundingMode.HALF_UP);
        BigDecimal leaveDays = JsonMaps.dec(Map.of(), "unused") == null ? new BigDecimal("1.5").multiply(BigDecimal.valueOf(Math.max(years, 1))) : BigDecimal.ZERO;
        BigDecimal leavePay = daily.multiply(leaveDays).setScale(2, RoundingMode.HALF_UP);
        BigDecimal severance = "TERMINATED".equalsIgnoreCase(e.getStatus())
                ? n(contract.getBaseSalary()).multiply(new BigDecimal("1.5")).multiply(BigDecimal.valueOf(years)).divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", employeeId);
        out.put("lastNet", last.getNet());
        out.put("leaveDays", leaveDays);
        out.put("leavePay", leavePay);
        out.put("severance", severance);
        out.put("total", n(last.getNet()).add(leavePay).add(severance));
        return out;
    }

    public Map<String, Object> opsStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mail", Map.of("enabled", mailEnabled));
        m.put("whatsapp", Map.of("enabled", whatsappEnabled));
        boolean minioOk = false;
        String minioDetail = minioEnabled ? "off" : "disabled";
        MinioClient client = minio.getIfAvailable();
        if (client != null) {
            try {
                client.listBuckets();
                minioOk = true;
                minioDetail = "ready";
            } catch (Exception ex) {
                minioOk = false;
                minioDetail = ex.getMessage();
            }
        }
        m.put("minio", Map.of("enabled", minioEnabled, "ready", minioOk, "detail", minioDetail));
        boolean redisOk = false;
        String redisDetail = redisEnabled ? "memory" : "disabled";
        RedisConnectionFactory factory = redis.getIfAvailable();
        if (factory != null) {
            try (var c = factory.getConnection()) {
                c.ping();
                redisOk = true;
                redisDetail = "ready";
            } catch (Exception ex) {
                redisDetail = ex.getMessage();
            }
        }
        m.put("redis", Map.of("ready", redisOk, "detail", redisDetail, "cache", cacheManager.getClass().getSimpleName()));
        m.put("whatsappOutbox", records.findByKindOrderByIdDesc(HrRecord.OUTBOUND).size());
        return m;
    }

    public Map<String, Object> publicJobs(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow(() -> BusinessException.notFound("Société introuvable"));
        boolean prev = TenantContext.isBypass();
        try {
            TenantContext.setBypass(true);
            TenantContext.setTenantId(tenant.getId());
            List<Map<String, Object>> jobs = new ArrayList<>();
            for (JobOffer offer : jobOfferRepository.findAll()) {
                if (!offer.isPublished() && !"OPEN".equalsIgnoreCase(offer.getStatus())) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", offer.getId());
                row.put("title", offer.getTitle());
                row.put("city", offer.getCity());
                row.put("description", offer.getDescription());
                jobs.add(row);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("company", tenant.getName());
            out.put("jobs", jobs);
            return out;
        } finally {
            TenantContext.setTenantId(null);
            TenantContext.setBypass(prev);
        }
    }

    @Transactional
    public Map<String, Object> applyPublic(String slug, Map<String, Object> body) {
        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow(() -> BusinessException.notFound("Société introuvable"));
        boolean prev = TenantContext.isBypass();
        try {
            TenantContext.setBypass(true);
            TenantContext.setTenantId(tenant.getId());
            Candidate c = new Candidate();
            c.setTenantId(tenant.getId());
            c.setFirstName(JsonMaps.str(body, "firstName"));
            c.setLastName(JsonMaps.str(body, "lastName"));
            c.setEmail(JsonMaps.str(body, "email"));
            c.setPhone(JsonMaps.str(body, "phone"));
            c.setJobOfferId(JsonMaps.lng(body, "jobOfferId", "jobId"));
            c.setCvText(trim(JsonMaps.str(body, "cvText", "cv"), 8000));
            c.setStatus("NEW");
            candidateRepository.save(c);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", c.getId());
            out.put("status", "RECEIVED");
            return out;
        } finally {
            TenantContext.setTenantId(null);
            TenantContext.setBypass(prev);
        }
    }

    public Map<String, Object> toDto(HrRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("kind", r.getKind());
        m.put("employeeId", r.getEmployeeId());
        Employee person = r.getEmployeeId() == null ? null : employeeRepository.findById(r.getEmployeeId()).orElse(null);
        m.put("employee", JsonMaps.employeeRef(person));
        if (person != null) {
            String first = person.getFirstName() == null ? "" : person.getFirstName();
            String last = person.getLastName() == null ? "" : person.getLastName();
            m.put("employeeName", (first + " " + last).trim());
        }
        m.put("relatedId", r.getRelatedId());
        if (r.getRelatedId() != null) {
            records.findById(r.getRelatedId()).ifPresent(rel -> m.put("relatedName", rel.getTitle()));
        }
        m.put("title", r.getTitle());
        m.put("name", r.getTitle());
        m.put("code", r.getCode());
        m.put("status", r.getStatus());
        m.put("notes", r.getNotes());
        m.put("amount", r.getAmount());
        m.put("remaining", r.getRemaining());
        m.put("monthlyAmount", r.getMonthlyAmount());
        m.put("startDate", r.getStartDate());
        m.put("endDate", r.getEndDate());
        m.put("startTime", r.getStartTime());
        m.put("endTime", r.getEndTime());
        m.put("latitude", r.getLatitude());
        m.put("longitude", r.getLongitude());
        m.put("radiusMeters", r.getRadiusMeters());
        m.put("payload", r.getPayload());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private void apply(HrRecord r, Map<String, Object> b) {
        if (b.containsKey("employeeId")) r.setEmployeeId(JsonMaps.lng(b, "employeeId"));
        if (b.containsKey("relatedId") || b.containsKey("establishmentId") || b.containsKey("shiftId") || b.containsKey("documentId")) {
            r.setRelatedId(JsonMaps.lng(b, "relatedId", "establishmentId", "shiftId", "documentId"));
        }
        if (b.containsKey("title") || b.containsKey("name")) r.setTitle(JsonMaps.str(b, "title", "name"));
        if (b.containsKey("code") || b.containsKey("serial") || b.containsKey("qrToken")) {
            r.setCode(JsonMaps.str(b, "code", "serial", "qrToken"));
        }
        if (b.containsKey("status")) r.setStatus(JsonMaps.str(b, "status"));
        if (b.containsKey("notes") || b.containsKey("reason")) r.setNotes(JsonMaps.str(b, "notes", "reason"));
        if (b.containsKey("amount") || b.containsKey("principal")) r.setAmount(n(JsonMaps.dec(b, "amount", "principal")));
        if (b.containsKey("remaining")) r.setRemaining(n(JsonMaps.dec(b, "remaining")));
        if (b.containsKey("monthlyAmount")) r.setMonthlyAmount(n(JsonMaps.dec(b, "monthlyAmount")));
        if (b.containsKey("startDate")) r.setStartDate(JsonMaps.date(b, "startDate"));
        if (b.containsKey("endDate")) r.setEndDate(JsonMaps.date(b, "endDate"));
        if (b.containsKey("startTime")) r.setStartTime(JsonMaps.time(b, "startTime"));
        if (b.containsKey("endTime")) r.setEndTime(JsonMaps.time(b, "endTime"));
        if (b.containsKey("latitude")) r.setLatitude(dbl(b, "latitude"));
        if (b.containsKey("longitude")) r.setLongitude(dbl(b, "longitude"));
        if (b.containsKey("radiusMeters")) r.setRadiusMeters(dbl(b, "radiusMeters"));
        if (b.containsKey("payload") || b.containsKey("channel")) r.setPayload(JsonMaps.str(b, "payload", "channel"));
    }

    private void requireKind(UserPrincipal actor, String kind, boolean write) {
        switch (kind) {
            case HrRecord.LOAN -> permissionService.require(actor, write ? PermissionCodes.PAYROLL_EDIT : PermissionCodes.PAYROLL_VIEW);
            case HrRecord.ESTABLISHMENT, HrRecord.SHIFT, HrRecord.SHIFT_ASSIGNMENT ->
                    permissionService.require(actor, write ? PermissionCodes.ATTENDANCE_MANAGE : PermissionCodes.ATTENDANCE_VIEW);
            case HrRecord.ASSET -> permissionService.require(actor, write ? PermissionCodes.EMPLOYEES_EDIT : PermissionCodes.EMPLOYEES_VIEW);
            case HrRecord.DISCIPLINARY -> permissionService.require(actor, PermissionCodes.EMPLOYEES_EDIT);
            case HrRecord.SIGNATURE, HrRecord.OUTBOUND -> permissionService.require(actor, PermissionCodes.DOCUMENTS_VIEW);
            default -> throw BusinessException.badRequest("Module inconnu");
        }
    }

    private Employee sample(Long id) {
        if (id != null) {
            return employeeRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
        }
        Employee e = new Employee();
        e.setDependents(0);
        e.setHireDate(LocalDate.now().minusYears(3));
        return e;
    }

    private static Double dbl(Map<String, Object> b, String key) {
        Object v = b.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String hash(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 32);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private static String trim(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }

    private static BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
