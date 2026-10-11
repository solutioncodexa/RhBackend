package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ma.rhflow.domain.entity.OnboardingTask;

public interface OnboardingTaskRepository extends JpaRepository<OnboardingTask, Long>, JpaSpecificationExecutor<OnboardingTask> {
    long countByEmployeeId(Long employeeId);

    long countByEmployeeIdAndStatus(Long employeeId, String status);
}
