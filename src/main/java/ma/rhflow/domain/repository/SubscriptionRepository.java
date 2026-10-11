package ma.rhflow.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ma.rhflow.domain.entity.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByTenantId(Long tenantId);
}
