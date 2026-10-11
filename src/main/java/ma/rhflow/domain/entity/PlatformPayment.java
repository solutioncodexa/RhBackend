package ma.rhflow.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "platform_payments")
@Getter
@Setter
public class PlatformPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long tenantId;
    private BigDecimal amount = BigDecimal.ZERO;
    private String method = "VIREMENT";
    private LocalDate paidOn;
    private String note;
    private Instant createdAt = Instant.now();
}
