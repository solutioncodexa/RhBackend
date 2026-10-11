package ma.rhflow.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ma.rhflow.domain.entity.PayrollRuleSet;

public interface PayrollRuleSetRepository extends JpaRepository<PayrollRuleSet, Long> {
    Optional<PayrollRuleSet> findByCode(String code);
    List<PayrollRuleSet> findByEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqual(LocalDate from, LocalDate to);
}
