package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

import ma.rhflow.domain.entity.UserPermission;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long>, JpaSpecificationExecutor<UserPermission> {
    List<UserPermission> findByUserIdAndGrantedTrue(Long userId);
    void deleteByUserId(Long userId);
}
