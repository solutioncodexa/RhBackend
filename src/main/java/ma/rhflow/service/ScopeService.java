package ma.rhflow.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.security.UserPrincipal;

@Service
@RequiredArgsConstructor
public class ScopeService {

    private final EmployeeRepository employeeRepository;

    public boolean unrestricted(UserPrincipal actor) {
        return actor != null && (actor.getRole() == UserRole.ADMIN || actor.getRole() == UserRole.SUPER_ADMIN);
    }

    public Set<Long> visibleEmployeeIds(UserPrincipal actor) {
        if (unrestricted(actor)) {
            return null;
        }
        Set<Long> ids = new HashSet<>();
        if (actor.getEmployeeId() != null) {
            ids.add(actor.getEmployeeId());
        }
        if (actor.getRole() == UserRole.MANAGER && actor.getEmployeeId() != null) {
            List<Employee> reports = employeeRepository.findByManagerId(actor.getEmployeeId());
            for (Employee e : reports) {
                ids.add(e.getId());
            }
        }
        if (ids.isEmpty()) {
            ids.add(-1L);
        }
        return ids;
    }

    public void assertEmployeeVisible(UserPrincipal actor, Long employeeId) {
        if (employeeId == null || unrestricted(actor)) {
            return;
        }
        Set<Long> ids = visibleEmployeeIds(actor);
        if (ids == null || ids.contains(employeeId)) {
            return;
        }
        throw BusinessException.forbidden("Hors de votre périmètre");
    }

    public boolean canApproveLeave(UserPrincipal actor, Long employeeId) {
        if (unrestricted(actor)) {
            return true;
        }
        if (actor.getRole() != UserRole.MANAGER || actor.getEmployeeId() == null || employeeId == null) {
            return false;
        }
        Employee target = employeeRepository.findById(employeeId).orElse(null);
        return target != null && actor.getEmployeeId().equals(target.getManagerId());
    }
}
