package ma.rhflow.web;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.ApiResponse;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.service.LeaveService;

@RestController
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @GetMapping("/leave-requests")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam Map<String, String> params,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(CatalogController.pageMap(leaveService.list(params, page, size, actor)));
    }

    @PostMapping("/leave-requests")
    public ApiResponse<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(leaveService.create(body, actor));
    }

    @PostMapping("/leave-requests/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(leaveService.decide(id, "approve", body, actor));
    }

    @PostMapping("/leave-requests/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(leaveService.decide(id, "reject", body, actor));
    }

    @GetMapping("/leave-requests/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        byte[] bytes = leaveService.authorizationPdf(id, actor);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"conge-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @PostMapping("/leave-requests/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(leaveService.cancel(id, actor));
    }
}
