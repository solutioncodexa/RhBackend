package ma.rhflow.domain.entity;

import java.time.Instant;

import org.hibernate.annotations.Filter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.tenant.TenantScoped;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Filter(name = TenantScoped.FILTER_NAME, condition = "tenant_id = :" + TenantScoped.PARAM_NAME)
public class AuditLog extends TenantScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String action;
    private String entityType;
    private String entityId;
    private String userEmail;
    private Long userId;
    @Column(length = 2000)
    private String details;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
