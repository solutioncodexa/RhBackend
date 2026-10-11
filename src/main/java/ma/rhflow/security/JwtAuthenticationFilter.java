package ma.rhflow.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import ma.rhflow.config.JwtProperties;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.tenant.TenantContext;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = extractBearer(request);
            if (StringUtils.hasText(token) && jwtService.validateAccessToken(token)) {
                Long userId = jwtService.getUserId(token);
                UserDetails userDetails = userId != null
                        ? userDetailsService.loadById(userId)
                        : userDetailsService.loadUserByUsername(jwtService.getEmail(token));
                if (userDetails.isEnabled() && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    applyTenant((UserPrincipal) userDetails, token, request);
                }
            }
        } catch (Exception e) {
            logger.warn("JWT filter: " + e.getMessage());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void applyTenant(UserPrincipal principal, String token, HttpServletRequest request) {
        boolean platform = request.getRequestURI() != null && request.getRequestURI().startsWith("/platform");
        if (principal.getRole() == UserRole.SUPER_ADMIN) {
            TenantContext.setBypass(true);
            if (!platform) {
                Long claimed = jwtService.getTenantId(token);
                if (claimed != null) {
                    TenantContext.setTenantId(claimed);
                    TenantContext.setBypass(false);
                }
            }
            return;
        }
        if (principal.getTenantId() != null) {
            TenantContext.setTenantId(principal.getTenantId());
        }
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader(jwtProperties.header());
        String prefix = jwtProperties.prefix() + " ";
        if (StringUtils.hasText(header) && header.startsWith(prefix)) {
            return header.substring(prefix.length());
        }
        return null;
    }
}
