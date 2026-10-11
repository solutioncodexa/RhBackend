package ma.rhflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long expirationMs,
        long refreshExpirationMs,
        long rememberMeRefreshExpirationMs,
        int maxRefreshTokensPerUser,
        String header,
        String prefix
) {
}
