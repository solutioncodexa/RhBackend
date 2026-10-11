package ma.rhflow.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.hibernate.annotations.Filter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.tenant.TenantScoped;

@Entity
@Table(name = "working_hours")
@Getter
@Setter
@Filter(name = TenantScoped.FILTER_NAME, condition = "tenant_id = :" + TenantScoped.PARAM_NAME)
public class WorkingHours extends TenantScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Long departmentId;
    private Long employeeId;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalTime breakStart;
    private LocalTime breakEnd;
    private BigDecimal weeklyHours;
    private String workDays;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
