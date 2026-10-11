package ma.rhflow.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.ApiResponse;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.service.UserAdminService;

@RestController
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping("/users")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(CatalogController.pageMap(userAdminService.list(q, page, size, actor)));
    }

    @PostMapping("/users")
    public ApiResponse<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(userAdminService.create(body, actor));
    }

    @GetMapping("/users/{id}/permissions")
    public ApiResponse<java.util.List<String>> permissions(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(userAdminService.permissions(id, actor));
    }

    @PutMapping("/users/{id}/permissions")
    public ApiResponse<java.util.List<String>> setPermissions(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(userAdminService.setPermissions(id, body, actor));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(userAdminService.update(id, body, actor));
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        userAdminService.delete(id, actor);
    }
}
