package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.time.LocalDate;
import java.util.List;

import ma.rhflow.domain.entity.Holiday;

public interface HolidayRepository extends JpaRepository<Holiday, Long>, JpaSpecificationExecutor<Holiday> {
    List<Holiday> findByDateBetween(LocalDate from, LocalDate to);
    boolean existsByDate(LocalDate date);
}
