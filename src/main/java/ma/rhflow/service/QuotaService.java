package ma.rhflow.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.domain.repository.UserRepository;
import ma.rhflow.tenant.TenantContext;

@Service
@RequiredArgsConstructor
public class QuotaService {

    private final TenantRepository tenantRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    public Tenant current() {
        Long id = TenantContext.requireTenantId();
        return tenantRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Société introuvable"));
    }

    public void assertEmployeeQuota() {
        Tenant t = current();
        var sub = subscriptionService.ensureFor(t);
        int max = sub.getMaxEmployees() == null ? t.getPlan().maxEmployees() : sub.getMaxEmployees();
        long count = employeeRepository.count();
        if (count >= max) {
            throw BusinessException.conflict(
                    "Quota salariés atteint (" + max + " pour le plan " + sub.getPlan().name() + "). Passez au plan supérieur.");
        }
    }

    public void assertUserQuota() {
        Tenant t = current();
        var sub = subscriptionService.ensureFor(t);
        int max = sub.getMaxUsers() == null ? t.getPlan().maxUsers() : sub.getMaxUsers();
        long count = userRepository.count();
        if (count >= max) {
            throw BusinessException.conflict(
                    "Quota utilisateurs atteint (" + max + " pour le plan " + sub.getPlan().name() + ").");
        }
    }
}
