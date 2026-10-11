package ma.rhflow.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ma.rhflow.domain.entity.HrRecord;

public interface HrRecordRepository extends JpaRepository<HrRecord, Long> {
    List<HrRecord> findByKindOrderByIdDesc(String kind);
    List<HrRecord> findByKindAndEmployeeId(String kind, Long employeeId);
    List<HrRecord> findByKindAndStatus(String kind, String status);
    Optional<HrRecord> findByKindAndCode(String kind, String code);
    List<HrRecord> findByKindAndRelatedId(String kind, Long relatedId);
}
