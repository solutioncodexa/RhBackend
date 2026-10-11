package ma.rhflow.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "payroll_rule_sets")
@Getter
@Setter
public class PayrollRuleSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private BigDecimal smigMonthly;
    private BigDecimal legalWeeklyHours;
    private BigDecimal cnssCap;
    private BigDecimal cnssSt;
    private BigDecimal cnssLt;
    private BigDecimal amoEmployee;
    private BigDecimal employerAf;
    private BigDecimal employerPsct;
    private BigDecimal employerPslt;
    private BigDecimal employerAmo;
    private BigDecimal employerTfp;
    private BigDecimal fpAnnualThreshold;
    private BigDecimal fpLowRate;
    private BigDecimal fpHighRate;
    private BigDecimal fpAnnualCap;
    private BigDecimal overtimeDay;
    private BigDecimal overtimeNight;
    private BigDecimal overtimeRest;
    private BigDecimal overtimeNoComp;
    private BigDecimal familyReductionAnnual;
    private Integer maxDependents;
    @OneToMany(mappedBy = "ruleSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayrollBracket> brackets = new ArrayList<>();
}
