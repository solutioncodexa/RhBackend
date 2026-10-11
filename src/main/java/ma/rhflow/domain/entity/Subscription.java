package ma.rhflow.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.domain.enums.SubscriptionPlan;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long tenantId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionPlan plan = SubscriptionPlan.ESSENTIEL;
    private LocalDate expiresAt;
    private Integer maxEmployees;
    private Integer maxUsers;
    private boolean featureOllama;
    private boolean featureWhatsapp;
    private boolean active = true;
    private BigDecimal priceMad = BigDecimal.ZERO;
    private Instant createdAt = Instant.now();
}
