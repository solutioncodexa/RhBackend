package ma.rhflow.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.PlatformPayment;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.entity.User;
import ma.rhflow.domain.enums.SubscriptionPlan;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.domain.repository.PlatformPaymentRepository;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.domain.repository.UserRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.util.JsonMaps;
import ma.rhflow.util.TokenUtils;

@Service
@RequiredArgsConstructor
public class PlatformService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final SubscriptionService subscriptionService;
    private final PlatformPaymentRepository paymentRepository;
    private final jakarta.persistence.EntityManager entityManager;
    private final FileStorageService fileStorageService;

    public Page<Map<String, Object>> list(int page, int size) {
        return tenantRepository.findAll(PageRequest.of(page, size, Sort.by("name"))).map(this::toDto);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, UserPrincipal actor) {
        String slug = TokenUtils.slugify(JsonMaps.str(body, "slug"));
        if (tenantRepository.existsBySlug(slug)) {
            throw BusinessException.conflict("Slug déjà utilisé");
        }
        Tenant t = new Tenant();
        t.setSlug(slug);
        t.setName(JsonMaps.str(body, "name"));
        t.setLegalName(t.getName());
        t.setIce(JsonMaps.str(body, "ice"));
        t.setCity(JsonMaps.str(body, "city"));
        t.setPlan(parsePlan(JsonMaps.str(body, "plan")));
        t.setFeatureOllama(t.getPlan() != SubscriptionPlan.ESSENTIEL);
        t.setActive(true);
        t.setTrialEndsAt(LocalDate.now().plusDays(14));
        t.setCurrency("MAD");
        tenantRepository.save(t);
        subscriptionService.ensureFor(t);

        String email = TokenUtils.normalizeEmail(JsonMaps.str(body, "adminEmail"));
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("E-mail admin déjà utilisé");
        }
        User admin = new User();
        admin.setTenantId(t.getId());
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(JsonMaps.str(body, "adminPassword")));
        admin.setFirstName(JsonMaps.str(body, "adminFirstName"));
        admin.setLastName(JsonMaps.str(body, "adminLastName"));
        admin.setRole(UserRole.ADMIN);
        admin.setLocale("fr");
        admin.setActive(true);
        userRepository.save(admin);
        permissionService.seedDefaults(admin);
        auditService.log(actor, "CREATE", "tenant", t.getId(), slug);
        return toDto(t);
    }

    @Transactional
    public Map<String, Object> patch(Long id, Map<String, Object> body, UserPrincipal actor) {
        Tenant t = tenantRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Société introuvable"));
        if (body.containsKey("active")) {
            t.setActive(Boolean.TRUE.equals(JsonMaps.bool(body, "active")));
        }
        if (body.containsKey("plan")) {
            t.setPlan(parsePlan(JsonMaps.str(body, "plan")));
            t.setFeatureOllama(t.getPlan() != SubscriptionPlan.ESSENTIEL);
        }
        if (body.containsKey("trialEndsAt")) {
            t.setTrialEndsAt(java.time.LocalDate.parse(String.valueOf(body.get("trialEndsAt")).substring(0, 10)));
        }
        tenantRepository.save(t);
        subscriptionService.syncPlan(t);
        auditService.log(actor, "PATCH", "tenant", id, null);
        return toDto(t);
    }

    @Transactional
    public Map<String, Object> recordPayment(Long id, Map<String, Object> body, UserPrincipal actor) {
        Tenant t = tenantRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Société introuvable"));
        PlatformPayment p = new PlatformPayment();
        p.setTenantId(t.getId());
        p.setAmount(JsonMaps.dec(body, "amount") == null ? java.math.BigDecimal.ZERO : JsonMaps.dec(body, "amount"));
        p.setMethod(JsonMaps.str(body, "method") == null ? "VIREMENT" : JsonMaps.str(body, "method"));
        p.setNote(JsonMaps.str(body, "note"));
        String paid = JsonMaps.str(body, "paidOn");
        p.setPaidOn(paid == null ? java.time.LocalDate.now() : java.time.LocalDate.parse(paid.substring(0, 10)));
        paymentRepository.save(p);
        if (body.containsKey("extendDays")) {
            Integer days = JsonMaps.integer(body, "extendDays");
            LocalDate base = t.getTrialEndsAt() == null || t.getTrialEndsAt().isBefore(LocalDate.now())
                    ? LocalDate.now() : t.getTrialEndsAt();
            t.setTrialEndsAt(base.plusDays(days == null ? 30 : days));
            tenantRepository.save(t);
            subscriptionService.ensureFor(t).setExpiresAt(t.getTrialEndsAt());
            subscriptionService.syncPlan(t);
        }
        auditService.log(actor, "PAY", "tenant", id, "virement");
        return toDto(t);
    }

    @Transactional
    public void deleteTenant(Long id, UserPrincipal actor) {
        Tenant t = tenantRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Société introuvable"));
        String[] statements = {
                "delete from rhflow.refresh_tokens where user_id in (select id from rhflow.users where tenant_id = ?)",
                "delete from rhflow.password_reset_tokens where user_id in (select id from rhflow.users where tenant_id = ?)",
                "delete from rhflow.notifications where tenant_id = ?",
                "delete from rhflow.payroll_items where tenant_id = ?",
                "delete from rhflow.payslips where tenant_id = ?",
                "delete from rhflow.payroll_credits where tenant_id = ?",
                "delete from rhflow.payroll_periods where tenant_id = ?",
                "delete from rhflow.leave_requests where tenant_id = ?",
                "delete from rhflow.leave_balances where tenant_id = ?",
                "delete from rhflow.attendance where tenant_id = ?",
                "delete from rhflow.contracts where tenant_id = ?",
                "delete from rhflow.documents where tenant_id = ?",
                "delete from rhflow.employee_requests where tenant_id = ?",
                "delete from rhflow.announcements where tenant_id = ?",
                "delete from rhflow.profile_changes where tenant_id = ?",
                "delete from rhflow.training_enrollments where tenant_id = ?",
                "delete from rhflow.training_courses where tenant_id = ?",
                "delete from rhflow.employees where tenant_id = ?",
                "delete from rhflow.user_permissions where tenant_id = ?",
                "delete from rhflow.audit_logs where tenant_id = ?",
                "delete from rhflow.subscriptions where tenant_id = ?",
                "delete from rhflow.platform_payments where tenant_id = ?"
        };
        entityManager.unwrap(org.hibernate.Session.class).doWork(connection -> {
            for (String sql : statements) {
                java.sql.Savepoint savepoint = connection.setSavepoint();
                try (java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, id);
                    statement.executeUpdate();
                    connection.releaseSavepoint(savepoint);
                } catch (java.sql.SQLException ex) {
                    connection.rollback(savepoint);
                }
            }
        });
        userRepository.findAll().stream().filter(u -> id.equals(u.getTenantId())).forEach(userRepository::delete);
        tenantRepository.delete(t);
        fileStorageService.deleteTenantFiles(id);
        auditService.log(actor, "DELETE", "tenant", id, t.getSlug());
    }

    private SubscriptionPlan parsePlan(String raw) {
        if (raw == null) {
            return SubscriptionPlan.ESSENTIEL;
        }
        return SubscriptionPlan.valueOf(raw.trim().toUpperCase());
    }

    private Map<String, Object> toDto(Tenant t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("slug", t.getSlug());
        m.put("name", t.getName());
        m.put("legalName", t.getLegalName());
        m.put("ice", t.getIce());
        m.put("city", t.getCity());
        m.put("plan", t.getPlan() == null ? null : t.getPlan().name());
        m.put("active", t.isActive());
        m.put("featureOllama", t.isFeatureOllama());
        m.put("trialEndsAt", t.getTrialEndsAt());
        var sub = subscriptionService.ensureFor(t);
        m.put("maxEmployees", sub.getMaxEmployees());
        m.put("maxUsers", sub.getMaxUsers());
        m.put("expiresAt", sub.getExpiresAt());
        return m;
    }
}
