package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ma.rhflow.domain.entity.OffboardingTask;

public interface OffboardingTaskRepository extends JpaRepository<OffboardingTask, Long>, JpaSpecificationExecutor<OffboardingTask> {
    long countByEmployeeId(Long employeeId);

    long countByEmployeeIdAndStatus(Long employeeId, String status);
}
