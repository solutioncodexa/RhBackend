package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ma.rhflow.domain.entity.WorkingHours;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long>, JpaSpecificationExecutor<WorkingHours> {}
