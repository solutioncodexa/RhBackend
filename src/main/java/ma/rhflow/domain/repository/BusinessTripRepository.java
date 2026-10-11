package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ma.rhflow.domain.entity.BusinessTrip;

public interface BusinessTripRepository extends JpaRepository<BusinessTrip, Long>, JpaSpecificationExecutor<BusinessTrip> {}
