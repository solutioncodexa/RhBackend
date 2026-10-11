package ma.rhflow.domain.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.domain.enums.SubscriptionPlan;

@Entity
@Table(name = "platform_plan_defaults")
@Getter
@Setter
public class PlatformPlanDefault {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private SubscriptionPlan plan;
    private Integer maxEmployees;
    private Integer maxUsers;
    private BigDecimal priceMad;
    private boolean featureOllama;
    private boolean featureWhatsapp;
    private String labelFr;
    private String labelAr;
}
