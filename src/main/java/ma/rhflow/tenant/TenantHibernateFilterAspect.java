package ma.rhflow.tenant;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Aspect
@Component
@Order(0)
public class TenantHibernateFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("target(org.springframework.data.repository.Repository)")
    public void enableTenantFilter() {
        if (TenantContext.isBypass()) {
            return;
        }
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter(TenantScoped.FILTER_NAME)
                .setParameter(TenantScoped.PARAM_NAME, tenantId);
    }
}
