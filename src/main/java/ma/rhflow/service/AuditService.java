package ma.rhflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.rhflow.domain.entity.AuditLog;
import ma.rhflow.domain.repository.AuditLogRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.tenant.TenantContext;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(UserPrincipal actor, String action, String entityType, Object entityId, String details) {
        AuditLog a = new AuditLog();
        a.setTenantId(TenantContext.getTenantId());
        a.setAction(action);
        a.setEntityType(entityType);
        a.setEntityId(entityId == null ? null : String.valueOf(entityId));
        if (actor != null) {
            a.setUserId(actor.getId());
            a.setUserEmail(actor.getEmail());
        }
        a.setDetails(sanitize(details));
        auditLogRepository.save(a);
    }

    private String sanitize(String details) {
        if (details == null) {
            return null;
        }
        return details.replaceAll("(?i)(rib|cin|salary|salaire|password|token)=[^,\\s]+", "$1=***");
    }
}
