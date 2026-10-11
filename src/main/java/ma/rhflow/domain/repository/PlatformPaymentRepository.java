package ma.rhflow.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ma.rhflow.domain.entity.PlatformPayment;

public interface PlatformPaymentRepository extends JpaRepository<PlatformPayment, Long> {
    List<PlatformPayment> findByTenantIdOrderByPaidOnDesc(Long tenantId);
}
