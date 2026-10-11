package ma.rhflow.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ma.rhflow.domain.entity.PayrollCredit;

public interface PayrollCreditRepository extends JpaRepository<PayrollCredit, Long> {
    List<PayrollCredit> findByEmployeeIdAndAppliedPeriodIdIsNull(Long employeeId);
    List<PayrollCredit> findByFromPeriodId(Long fromPeriodId);
    List<PayrollCredit> findByAppliedPeriodId(Long appliedPeriodId);
}
