package ma.rhflow.domain.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "payroll_brackets")
@Getter
@Setter
public class PayrollBracket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "rule_set_id")
    private PayrollRuleSet ruleSet;
    private BigDecimal minInclusive;
    private BigDecimal maxInclusive;
    private BigDecimal rate;
    private BigDecimal deduction;
}
