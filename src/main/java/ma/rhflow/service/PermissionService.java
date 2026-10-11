package ma.rhflow.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.User;
import ma.rhflow.domain.entity.UserPermission;
import ma.rhflow.domain.enums.PermissionCodes;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.domain.repository.UserPermissionRepository;
import ma.rhflow.domain.repository.UserRepository;
import ma.rhflow.security.UserPrincipal;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UserPermissionRepository userPermissionRepository;
    private final UserRepository userRepository;

    @Transactional
    public void seedDefaults(User user) {
        if (user.getTenantId() == null || user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }
        Set<String> defaults = switch (user.getRole()) {
            case MANAGER -> PermissionCodes.DEFAULT_MANAGER;
            case EMPLOYEE -> PermissionCodes.DEFAULT_EMPLOYEE;
            default -> Set.of();
        };
        replacePermissions(user, defaults);
    }

    @Transactional
    public void grantMissingDefaults() {
        for (User user : userRepository.findAll()) {
            if (user.getRole() != UserRole.MANAGER && user.getRole() != UserRole.EMPLOYEE) {
                continue;
            }
            Set<String> defaults = user.getRole() == UserRole.MANAGER
                    ? PermissionCodes.DEFAULT_MANAGER
                    : PermissionCodes.DEFAULT_EMPLOYEE;
            Set<String> granted = new LinkedHashSet<>(userPermissionRepository.findByUserIdAndGrantedTrue(user.getId()).stream()
                    .map(UserPermission::getPermissionCode)
                    .toList());
            if (granted.isEmpty()) {
                seedDefaults(user);
                continue;
            }
            Instant now = Instant.now();
            for (String code : defaults) {
                if (granted.contains(code)) {
                    continue;
                }
                UserPermission permission = new UserPermission();
                permission.setTenantId(user.getTenantId());
                permission.setUserId(user.getId());
                permission.setPermissionCode(code);
                permission.setGranted(true);
                permission.setCreatedAt(now);
                permission.setUpdatedAt(now);
                userPermissionRepository.save(permission);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<String> resolvePermissions(User user) {
        if (user.getRole() == UserRole.ADMIN) {
            return List.of("*");
        }
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            return List.of("PLATFORM");
        }
        List<String> granted = userPermissionRepository.findByUserIdAndGrantedTrue(user.getId()).stream()
                .map(UserPermission::getPermissionCode)
                .sorted()
                .toList();
        if (!granted.isEmpty()) {
            return granted;
        }
        return switch (user.getRole()) {
            case MANAGER -> PermissionCodes.DEFAULT_MANAGER.stream().sorted().toList();
            case EMPLOYEE -> PermissionCodes.DEFAULT_EMPLOYEE.stream().sorted().toList();
            default -> List.of();
        };
    }

    @Transactional(readOnly = true)
    public boolean has(UserPrincipal principal, String code) {
        if (principal == null || code == null) {
            return false;
        }
        if (principal.getRole() == UserRole.ADMIN) {
            return true;
        }
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            return code.startsWith("PLATFORM") || "*".equals(code);
        }
        User user = userRepository.findById(principal.getId()).orElse(null);
        if (user == null) {
            return false;
        }
        List<String> perms = resolvePermissions(user);
        return perms.contains("*") || perms.contains(code);
    }

    public void require(UserPrincipal principal, String code) {
        if (!has(principal, code)) {
            throw BusinessException.forbidden("Permission requise : " + code);
        }
    }

    public void requireAny(UserPrincipal principal, String... codes) {
        for (String c : codes) {
            if (has(principal, c)) {
                return;
            }
        }
        throw BusinessException.forbidden("Permission insuffisante");
    }

    @Transactional
    public void replacePermissions(User user, Set<String> codes) {
        userPermissionRepository.deleteByUserId(user.getId());
        Instant now = Instant.now();
        for (String code : codes) {
            UserPermission p = new UserPermission();
            p.setTenantId(user.getTenantId());
            p.setUserId(user.getId());
            p.setPermissionCode(code);
            p.setGranted(true);
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            userPermissionRepository.save(p);
        }
    }

    public List<String> catalog() {
        return new ArrayList<>(PermissionCodes.ALL);
    }

    @Transactional
    public List<String> updateUserPermissions(Long userId, List<String> codes, UserPrincipal actor) {
        require(actor, PermissionCodes.USERS_MANAGE);
        User target = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("Utilisateur introuvable"));
        if (target.getRole() == UserRole.ADMIN || target.getRole() == UserRole.SUPER_ADMIN) {
            throw BusinessException.badRequest("Impossible de modifier les permissions d'un administrateur");
        }
        Set<String> set = new LinkedHashSet<>();
        if (codes != null) {
            for (String c : codes) {
                if (!PermissionCodes.isKnown(c)) {
                    throw BusinessException.badRequest("Permission inconnue : " + c);
                }
                set.add(c);
            }
        }
        replacePermissions(target, set);
        return resolvePermissions(target);
    }
}
