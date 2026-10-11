package ma.rhflow.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.ApiResponse;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.service.AuthService;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(authService.login(body));
    }

    @PostMapping("/auth/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(authService.refresh(body));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) Map<String, Object> body) {
        authService.logout(body == null ? Map.of() : body);
        return ApiResponse.message("Déconnecté");
    }

    @PostMapping("/auth/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok("Société créée", authService.register(body));
    }

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgot(@RequestBody Map<String, Object> body) {
        authService.forgotPassword(body);
        return ResponseEntity.ok(ApiResponse.message("Si un compte existe, un lien a été envoyé."));
    }

    @PostMapping("/auth/reset-password")
    public ApiResponse<Void> reset(@RequestBody Map<String, Object> body) {
        authService.resetPassword(body);
        return ApiResponse.message("Mot de passe mis à jour");
    }

    @GetMapping({"/me", "/auth/me"})
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(authService.me(principal));
    }
}
