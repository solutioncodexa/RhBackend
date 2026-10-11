package ma.rhflow.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import ma.rhflow.config.JwtProperties;
import ma.rhflow.domain.enums.UserRole;

@Slf4j
@Component
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey key;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String email, UserRole role, Long tenantId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.expirationMs());
        var builder = Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role.name())
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiry);
        if (tenantId != null) {
            builder.claim("tenantId", tenantId);
        }
        return builder.signWith(key).compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
            return null;
        } catch (MalformedJwtException | SignatureException | IllegalArgumentException e) {
            log.debug("JWT invalid: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateAccessToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }
        Object type = claims.get("type");
        return type == null || "access".equals(String.valueOf(type));
    }

    public String getEmail(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    public Long getTenantId(String token) {
        return number(parseToken(token), "tenantId");
    }

    public Long getUserId(String token) {
        return number(parseToken(token), "userId");
    }

    private Long number(Claims claims, String name) {
        if (claims == null) {
            return null;
        }
        Object raw = claims.get(name);
        if (raw instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
