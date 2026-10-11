package ma.rhflow.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.User;
import ma.rhflow.domain.enums.PermissionCodes;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.domain.repository.UserRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.tenant.TenantContext;
import ma.rhflow.util.JsonMaps;
import ma.rhflow.util.TokenUtils;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final AuthService authService;
    private final QuotaService quotaService;

    public Page<Map<String, Object>> list(String q, int page, int size, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.USERS_MANAGE);
        Long tenantId = TenantContext.requireTenantId();
        Specification<User> spec = (root, query, cb) -> {
            var p = cb.equal(root.get("tenantId"), tenantId);
            if (StringUtils.hasText(q)) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                p = cb.and(p, cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like)
                ));
            }
            return p;
        };
        return userRepository.findAll(spec, PageRequest.of(page, size, Sort.by("lastName")))
                .map(authService::toUserDto);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.USERS_MANAGE);
        quotaService.assertUserQuota();
        String email = TokenUtils.normalizeEmail(JsonMaps.str(body, "email"));
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("E-mail déjà utilisé");
        }
        User u = new User();
        u.setTenantId(TenantContext.requireTenantId());
        apply(u, body, true);
        u.setEmail(email);
        userRepository.save(u);
        permissionService.seedDefaults(u);
        if (!StringUtils.hasText(JsonMaps.str(body, "password"))) {
            authService.sendInvite(u);
        }
        Object perms = body.get("permissions");
        if (perms instanceof List<?> list) {
            permissionService.replacePermissions(u, Set.copyOf(list.stream().map(String::valueOf).toList()));
        }
        auditService.log(actor, "CREATE", "user", u.getId(), u.getEmail());
        return authService.toUserDto(u);
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.USERS_MANAGE);
        User u = userRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Utilisateur introuvable"));
        if (!TenantContext.requireTenantId().equals(u.getTenantId())) {
            throw BusinessException.notFound("Utilisateur introuvable");
        }
        apply(u, body, false);
        userRepository.save(u);
        Object perms = body.get("permissions");
        if (perms instanceof List<?> list) {
            permissionService.replacePermissions(u, Set.copyOf(list.stream().map(String::valueOf).toList()));
        }
        auditService.log(actor, "UPDATE", "user", u.getId(), u.getEmail());
        return authService.toUserDto(u);
    }

    @Transactional
    public void delete(Long id, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.USERS_MANAGE);
        User u = userRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Utilisateur introuvable"));
        if (u.getRole() == UserRole.ADMIN) {
            throw BusinessException.badRequest("Impossible de supprimer un admin");
        }
        userRepository.delete(u);
        auditService.log(actor, "DELETE", "user", id, u.getEmail());
    }

    public List<String> permissions(Long id, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.USERS_MANAGE);
        User u = userRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Utilisateur introuvable"));
        return permissionService.resolvePermissions(u);
    }

    @Transactional
    public List<String> setPermissions(Long id, Map<String, Object> body, UserPrincipal actor) {
        Object raw = body.get("permissions");
        if (!(raw instanceof List<?> list)) {
            throw BusinessException.badRequest("permissions requis");
        }
        return permissionService.updateUserPermissions(id, list.stream().map(String::valueOf).toList(), actor);
    }

    private void apply(User u, Map<String, Object> body, boolean creating) {
        if (body.containsKey("firstName")) u.setFirstName(JsonMaps.str(body, "firstName"));
        if (body.containsKey("lastName")) u.setLastName(JsonMaps.str(body, "lastName"));
        if (body.containsKey("locale")) u.setLocale(JsonMaps.str(body, "locale"));
        if (body.containsKey("role")) {
            String role = JsonMaps.str(body, "role");
            if ("SUPER_ADMIN".equals(role)) {
                throw BusinessException.badRequest("Rôle interdit");
            }
            u.setRole(UserRole.valueOf(role));
        }
        if (body.containsKey("employeeId")) u.setEmployeeId(JsonMaps.lng(body, "employeeId"));
        if (body.containsKey("active")) u.setActive(Boolean.TRUE.equals(JsonMaps.bool(body, "active")));
        String password = JsonMaps.str(body, "password");
        if (StringUtils.hasText(password)) {
            u.setPasswordHash(passwordEncoder.encode(password));
        } else if (creating) {
            u.setPasswordHash(passwordEncoder.encode(TokenUtils.randomToken()));
        }
        if (u.getLocale() == null) {
            u.setLocale("fr");
        }
        if (creating) {
            u.setActive(true);
        }
    }
}
