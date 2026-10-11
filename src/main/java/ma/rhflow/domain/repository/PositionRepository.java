package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ma.rhflow.domain.entity.Position;

public interface PositionRepository extends JpaRepository<Position, Long>, JpaSpecificationExecutor<Position> {}
