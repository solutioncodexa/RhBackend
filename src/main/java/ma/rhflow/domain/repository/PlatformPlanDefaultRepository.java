package ma.rhflow.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ma.rhflow.domain.entity.PlatformPlanDefault;
import ma.rhflow.domain.enums.SubscriptionPlan;

public interface PlatformPlanDefaultRepository extends JpaRepository<PlatformPlanDefault, Long> {
    Optional<PlatformPlanDefault> findByPlan(SubscriptionPlan plan);
}
