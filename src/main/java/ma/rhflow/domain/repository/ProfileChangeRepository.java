package ma.rhflow.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ma.rhflow.domain.entity.ProfileChange;

public interface ProfileChangeRepository extends JpaRepository<ProfileChange, Long>, JpaSpecificationExecutor<ProfileChange> {
    List<ProfileChange> findByEmployeeIdAndFieldNameAndStatus(Long employeeId, String fieldName, String status);
}
