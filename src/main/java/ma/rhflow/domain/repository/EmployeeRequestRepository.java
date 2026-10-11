package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ma.rhflow.domain.entity.EmployeeRequest;

public interface EmployeeRequestRepository extends JpaRepository<EmployeeRequest, Long>, JpaSpecificationExecutor<EmployeeRequest> {}
