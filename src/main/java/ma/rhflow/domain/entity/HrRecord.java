package ma.rhflow.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

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
@Table(name = "hr_records")
@Getter
@Setter
@Filter(name = TenantScoped.FILTER_NAME, condition = "tenant_id = :" + TenantScoped.PARAM_NAME)
public class HrRecord extends TenantScoped {

    public static final String ESTABLISHMENT = "ESTABLISHMENT";
    public static final String SHIFT = "SHIFT";
    public static final String SHIFT_ASSIGNMENT = "SHIFT_ASSIGNMENT";
    public static final String LOAN = "LOAN";
    public static final String ASSET = "ASSET";
    public static final String DISCIPLINARY = "DISCIPLINARY";
    public static final String SIGNATURE = "SIGNATURE";
    public static final String OUTBOUND = "OUTBOUND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String kind;
    private Long employeeId;
    private Long relatedId;
    private String title;
    private String code;
    private String status;
    @Column(length = 4000)
    private String notes;
    private BigDecimal amount = BigDecimal.ZERO;
    private BigDecimal remaining = BigDecimal.ZERO;
    private BigDecimal monthlyAmount = BigDecimal.ZERO;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Double latitude;
    private Double longitude;
    private Double radiusMeters;
    @Column(length = 4000)
    private String payload;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
