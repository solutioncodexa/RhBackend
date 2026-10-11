package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ma.rhflow.domain.entity.HrDocument;

public interface HrDocumentRepository extends JpaRepository<HrDocument, Long>, JpaSpecificationExecutor<HrDocument> {}
