package ma.rhflow.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.config.JwtProperties;
import ma.rhflow.domain.entity.PasswordResetToken;
import ma.rhflow.domain.entity.RefreshToken;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.entity.User;
import ma.rhflow.domain.enums.SubscriptionPlan;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.domain.repository.PasswordResetTokenRepository;
import ma.rhflow.domain.repository.RefreshTokenRepository;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.domain.repository.UserRepository;
import ma.rhflow.security.JwtService;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.util.TokenUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PermissionService permissionService;
    private final MailService mailService;
    private final AuditService auditService;
    private final SubscriptionService subscriptionService;

    @Value("${app.frontend-url:http://localhost:4300}")
    private String frontendUrl;

    @Transactional
    public Map<String, Object> login(Map<String, Object> body) {
        String email = TokenUtils.normalizeEmail(JsonMapsSafe.str(body, "email"));
        String password = JsonMapsSafe.str(body, "password");
        String deviceType = JsonMapsSafe.str(body, "deviceType");
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw BusinessException.badRequest("Email et mot de passe requis");
        }
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> BusinessException.unauthorized("Email ou mot de passe incorrect."));
        if (!user.isActive()) {
            throw BusinessException.forbidden("Compte désactivé.");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw BusinessException.unauthorized("Email ou mot de passe incorrect.");
        }
        if (user.getTenantId() != null) {
            Tenant tenant = tenantRepository.findById(user.getTenantId())
                    .orElseThrow(() -> BusinessException.notFound("Société introuvable"));
            if (!tenant.isActive()) {
                throw BusinessException.forbidden("Société inactive.");
            }
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        boolean remember = Boolean.TRUE.equals(ma.rhflow.util.JsonMaps.bool(body, "rememberMe", "remember"));
        return issueTokens(user, deviceType == null ? "WEB" : deviceType, remember);
    }

    @Transactional
    public Map<String, Object> refresh(Map<String, Object> body) {
        String raw = JsonMapsSafe.str(body, "refreshToken");
        if (!StringUtils.hasText(raw)) {
            throw BusinessException.unauthorized("Refresh token manquant");
        }
        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenUtils.sha256(raw))
                .orElseThrow(() -> BusinessException.unauthorized("Refresh token invalide"));
        if (!stored.isUsable()) {
            throw BusinessException.unauthorized("Refresh token expiré");
        }
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        return issueTokens(stored.getUser(), stored.getDeviceType(), false);
    }

    @Transactional
    public void logout(Map<String, Object> body) {
        String raw = JsonMapsSafe.str(body, "refreshToken");
        if (!StringUtils.hasText(raw)) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenUtils.sha256(raw)).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    @Transactional
    public Map<String, Object> register(Map<String, Object> body) {
        String email = TokenUtils.normalizeEmail(JsonMapsSafe.str(body, "email"));
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("Cet e-mail est déjà utilisé");
        }
        Tenant tenant = new Tenant();
        tenant.setName(JsonMapsSafe.str(body, "companyName"));
        tenant.setLegalName(tenant.getName());
        tenant.setIce(JsonMapsSafe.str(body, "ice"));
        tenant.setCity(JsonMapsSafe.str(body, "city"));
        tenant.setSlug(uniqueSlug(TokenUtils.slugify(tenant.getName())));
        tenant.setPlan(SubscriptionPlan.ESSENTIEL);
        tenant.setFeatureOllama(false);
        tenant.setActive(true);
        tenant.setTrialEndsAt(LocalDate.now().plusDays(14));
        tenant.setCurrency("MAD");
        tenantRepository.save(tenant);
        subscriptionService.ensureFor(tenant);

        User admin = new User();
        admin.setTenantId(tenant.getId());
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(JsonMapsSafe.str(body, "password")));
        admin.setFirstName(JsonMapsSafe.str(body, "firstName"));
        admin.setLastName(JsonMapsSafe.str(body, "lastName"));
        admin.setRole(UserRole.ADMIN);
        admin.setLocale("fr");
        admin.setActive(true);
        userRepository.save(admin);
        permissionService.seedDefaults(admin);
        auditService.log(null, "REGISTER", "Tenant", tenant.getId(), "self-register");
        return Map.of("tenantId", tenant.getId(), "userId", admin.getId());
    }

    @Transactional
    public void forgotPassword(Map<String, Object> body) {
        String email = TokenUtils.normalizeEmail(JsonMapsSafe.str(body, "email"));
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            String raw = TokenUtils.randomToken();
            PasswordResetToken t = new PasswordResetToken();
            t.setUser(user);
            t.setTokenHash(TokenUtils.sha256(raw));
            t.setExpiresAt(Instant.now().plusSeconds(3600));
            t.setPurpose("RESET");
            passwordResetTokenRepository.save(t);
            mailService.send(user.getEmail(), "Réinitialisation RHFlow",
                    "Vous avez demandé à réinitialiser votre mot de passe. Ce lien est valable une heure.",
                    "/reset-password?token=" + raw, "Choisir un mot de passe");
        });
    }

    @Transactional
    public void resetPassword(Map<String, Object> body) {
        String token = JsonMapsSafe.str(body, "token");
        String password = JsonMapsSafe.str(body, "password");
        PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(TokenUtils.sha256(token))
                .orElseThrow(() -> BusinessException.badRequest("Lien invalide"));
        if (stored.isUsed() || Instant.now().isAfter(stored.getExpiresAt())) {
            throw BusinessException.badRequest("Lien expiré");
        }
        User user = stored.getUser();
        user.setPasswordHash(passwordEncoder.encode(password));
        stored.setUsed(true);
        userRepository.save(user);
        passwordResetTokenRepository.save(stored);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> me(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> BusinessException.notFound("Utilisateur introuvable"));
        return toUserDto(user);
    }

    public Map<String, Object> toUserDto(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", user.getId());
        m.put("email", user.getEmail());
        m.put("firstName", user.getFirstName());
        m.put("lastName", user.getLastName());
        m.put("role", user.getRole().name());
        m.put("tenantId", user.getTenantId());
        m.put("permissions", permissionService.resolvePermissions(user));
        m.put("locale", user.getLocale());
        m.put("employeeId", user.getEmployeeId());
        m.put("active", user.isActive());
        return m;
    }

    public void sendInvite(User user) {
        String raw = TokenUtils.randomToken();
        PasswordResetToken t = new PasswordResetToken();
        t.setUser(user);
        t.setTokenHash(TokenUtils.sha256(raw));
        t.setExpiresAt(Instant.now().plusSeconds(7L * 24 * 3600));
        t.setPurpose("INVITE");
        passwordResetTokenRepository.save(t);
        mailService.send(user.getEmail(), "Invitation RHFlow",
                "Votre compte RHFlow est prêt. Ce lien est valable 7 jours.",
                "/reset-password?token=" + raw, "Activer mon compte");
    }

    private Map<String, Object> issueTokens(User user, String deviceType, boolean remember) {
        String access = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), user.getTenantId());
        String refresh = TokenUtils.randomToken();
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(TokenUtils.sha256(refresh));
        rt.setDeviceType(deviceType);
        long ttl = remember ? jwtProperties.rememberMeRefreshExpirationMs() : jwtProperties.refreshExpirationMs();
        rt.setExpiresAt(Instant.now().plusMillis(ttl));
        refreshTokenRepository.save(rt);
        Map<String, Object> userDto = toUserDto(user);
        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("accessToken", access);
        tokens.put("refreshToken", refresh);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", access);
        data.put("refreshToken", refresh);
        data.put("user", userDto);
        data.put("tokens", tokens);
        return data;
    }

    private String uniqueSlug(String base) {
        String slug = base;
        int i = 1;
        while (tenantRepository.existsBySlug(slug)) {
            slug = base + "-" + i++;
        }
        return slug;
    }

    static final class JsonMapsSafe {
        static String str(Map<String, Object> m, String k) {
            if (m == null || m.get(k) == null) {
                return null;
            }
            return String.valueOf(m.get(k)).trim();
        }
    }
}
