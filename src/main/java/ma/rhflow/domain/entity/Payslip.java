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
@Table(name = "payslips")
@Getter
@Setter
@Filter(name = TenantScoped.FILTER_NAME, condition = "tenant_id = :" + TenantScoped.PARAM_NAME)
public class Payslip extends TenantScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long periodId;
    private Long employeeId;
    private Long ruleSetId;
    private Integer year;
    private Integer month;
    private BigDecimal baseSalary = BigDecimal.ZERO;
    private BigDecimal seniorityAmount = BigDecimal.ZERO;
    private BigDecimal bonuses = BigDecimal.ZERO;
    private BigDecimal overtimeAmount = BigDecimal.ZERO;
    private BigDecimal unpaidAbsence = BigDecimal.ZERO;
    private BigDecimal advances = BigDecimal.ZERO;
    private BigDecimal garnishments = BigDecimal.ZERO;
    private BigDecimal loanDeduction = BigDecimal.ZERO;
    private BigDecimal creditNote = BigDecimal.ZERO;
    private BigDecimal cimrEmployee = BigDecimal.ZERO;
    private BigDecimal cimrEmployer = BigDecimal.ZERO;
    private BigDecimal mutualEmployee = BigDecimal.ZERO;
    private BigDecimal mutualEmployer = BigDecimal.ZERO;
    private Integer declaredDays = 26;
    private Integer unpaidDays = 0;
    private Integer paidLeaveDays = 0;
    private BigDecimal indemnities = BigDecimal.ZERO;
    @Column(length = 2000)
    private String linkNote;
    private String snapshotCnss;
    private String snapshotRib;
    private Instant emailedAt;
    private BigDecimal deductions = BigDecimal.ZERO;
    private BigDecimal gross = BigDecimal.ZERO;
    private BigDecimal cnss = BigDecimal.ZERO;
    private BigDecimal amo = BigDecimal.ZERO;
    private BigDecimal ir = BigDecimal.ZERO;
    private BigDecimal net = BigDecimal.ZERO;
    private BigDecimal employerCnss = BigDecimal.ZERO;
    private BigDecimal employerAmo = BigDecimal.ZERO;
    private BigDecimal employerAf = BigDecimal.ZERO;
    private BigDecimal employerPsct = BigDecimal.ZERO;
    private BigDecimal employerPslt = BigDecimal.ZERO;
    private BigDecimal employerTfp = BigDecimal.ZERO;
    private BigDecimal employerCost = BigDecimal.ZERO;
    private boolean smigAlert;
    private String transferStatus = "PENDING";
    private LocalDate transferDate;
    private String transferReference;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
