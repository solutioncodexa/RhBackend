package ma.rhflow.tenant;

import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.lang.NonNull;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

public class TenantAwareJpaRepository<T, ID> extends SimpleJpaRepository<T, ID> {

    public TenantAwareJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
    }

    @Override
    protected <S extends T> TypedQuery<S> getQuery(Specification<S> spec, Class<S> domainClass, Sort sort) {
        return super.getQuery(withTenant(spec), domainClass, sort);
    }

    @Override
    protected <S extends T> TypedQuery<Long> getCountQuery(Specification<S> spec, Class<S> domainClass) {
        return super.getCountQuery(withTenant(spec), domainClass);
    }

    @Override
    public Optional<T> findById(@NonNull ID id) {
        Optional<T> found = super.findById(id);
        if (found.isEmpty() || sameTenant(found.get())) {
            return found;
        }
        return Optional.empty();
    }

    @Override
    public boolean existsById(@NonNull ID id) {
        return findById(id).isPresent();
    }

    @Override
    public Optional<T> findOne(Specification<T> spec) {
        return super.findOne(withTenant(spec));
    }

    private boolean sameTenant(T entity) {
        if (!(entity instanceof TenantScoped scoped) || TenantContext.isBypass()) {
            return true;
        }
        Long tenantId = TenantContext.getTenantId();
        return tenantId == null || tenantId.equals(scoped.getTenantId());
    }

    @Override
    public boolean exists(Specification<T> spec) {
        return super.exists(withTenant(spec));
    }

    @Override
    public long delete(Specification<T> spec) {
        return super.delete(withTenant(spec));
    }

    private <S extends T> Specification<S> withTenant(Specification<S> spec) {
        if (!TenantScoped.class.isAssignableFrom(getDomainClass()) || TenantContext.isBypass()) {
            return spec;
        }
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return spec;
        }
        Specification<S> tenant = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        return spec == null ? tenant : tenant.and(spec);
    }
}
