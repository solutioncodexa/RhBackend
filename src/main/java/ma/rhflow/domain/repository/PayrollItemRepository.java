package ma.rhflow.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ma.rhflow.domain.entity.PayrollItem;

public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long>, JpaSpecificationExecutor<PayrollItem> {
    List<PayrollItem> findByYearAndMonth(Integer year, Integer month);
}
