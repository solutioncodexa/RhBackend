package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ma.rhflow.domain.entity.TrainingEnrollment;

public interface TrainingEnrollmentRepository extends JpaRepository<TrainingEnrollment, Long>, JpaSpecificationExecutor<TrainingEnrollment> {
}
