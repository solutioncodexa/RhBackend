package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ma.rhflow.domain.entity.Objective;

public interface ObjectiveRepository extends JpaRepository<Objective, Long>, JpaSpecificationExecutor<Objective> {}
