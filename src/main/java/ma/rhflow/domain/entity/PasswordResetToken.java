package ma.rhflow.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private User user;
    @Column(nullable = false, unique = true, length = 128)
    private String tokenHash;
    @Column(nullable = false)
    private Instant expiresAt;
    private boolean used;
    @Column(length = 20)
    private String purpose = "RESET";
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
