package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ma.rhflow.domain.entity.TrainingCourse;

public interface TrainingCourseRepository extends JpaRepository<TrainingCourse, Long>, JpaSpecificationExecutor<TrainingCourse> {
}
