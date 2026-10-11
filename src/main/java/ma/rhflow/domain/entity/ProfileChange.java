package ma.rhflow.domain.entity;

import java.time.Instant;

import org.hibernate.annotations.Filter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.tenant.TenantScoped;

@Entity
@Table(name = "profile_changes")
@Getter
@Setter
@Filter(name = TenantScoped.FILTER_NAME, condition = "tenant_id = :" + TenantScoped.PARAM_NAME)
public class ProfileChange extends TenantScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long employeeId;
    private String fieldName;
    @Column(length = 2000)
    private String currentValue;
    @Column(length = 2000)
    private String proposedValue;
    private String status = "PENDING";
    @Column(length = 2000)
    private String decisionComment;
    private Long decidedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
