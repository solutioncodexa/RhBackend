package ma.rhflow.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Filter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.tenant.TenantScoped;

@Entity
@Table(name = "payroll_credits")
@Getter
@Setter
@Filter(name = TenantScoped.FILTER_NAME, condition = "tenant_id = :" + TenantScoped.PARAM_NAME)
public class PayrollCredit extends TenantScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long fromPeriodId;
    private Long appliedPeriodId;
    private Long employeeId;
    private BigDecimal amount = BigDecimal.ZERO;
    private String reason;
    private Instant createdAt = Instant.now();
}
