package ma.rhflow.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.domain.enums.SubscriptionPlan;

@Entity
@Table(name = "tenants")
@Getter
@Setter
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false)
    private String name;

    private String legalName;
    @Column(length = 15)
    private String ice;
    private String taxId;
    private String rc;
    private String cnssAffiliation;
    private String email;
    private String phone;
    private String city;
    private String address;
    private String website;
    private String sector;
    private String logoPath;
    private String currency = "MAD";
    private String whatsappPhoneNumberId;
    private String whatsappBusinessAccountId;
    private String whatsappAccessToken;
    private boolean whatsappEnabled;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionPlan plan = SubscriptionPlan.ESSENTIEL;
    private boolean featureOllama;
    private BigDecimal cimrEmployeeRate = BigDecimal.ZERO;
    private BigDecimal cimrEmployerRate = BigDecimal.ZERO;
    private BigDecimal mutualEmployeeRate = BigDecimal.ZERO;
    private BigDecimal mutualEmployerRate = BigDecimal.ZERO;
    private boolean active = true;
    private LocalDate trialEndsAt;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
