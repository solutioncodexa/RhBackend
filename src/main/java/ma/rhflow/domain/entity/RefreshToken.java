package ma.rhflow.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;
    private String deviceType = "WEB";
    @Column(nullable = false)
    private Instant expiresAt;
    private boolean revoked;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isUsable() {
        return !revoked && Instant.now().isBefore(expiresAt);
    }
}
