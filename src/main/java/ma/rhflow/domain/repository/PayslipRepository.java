package ma.rhflow.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;

import ma.rhflow.domain.entity.Payslip;

public interface PayslipRepository extends JpaRepository<Payslip, Long>, JpaSpecificationExecutor<Payslip> {
    List<Payslip> findByPeriodId(Long periodId);
    List<Payslip> findByYear(Integer year);
    Optional<Payslip> findByPeriodIdAndEmployeeId(Long periodId, Long employeeId);
    @Modifying
    void deleteByPeriodId(Long periodId);
}
