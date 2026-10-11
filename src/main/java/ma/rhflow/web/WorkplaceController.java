package ma.rhflow.web;

import java.util.List;
import java.util.Map;

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
import ma.rhflow.service.WorkplaceService;

@RestController
@RequiredArgsConstructor
public class WorkplaceController {

    private final WorkplaceService workplaceService;

    @GetMapping("/profile-changes")
    public ApiResponse<List<Map<String, Object>>> changes(@AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(workplaceService.listChanges(actor));
    }

    @PostMapping("/profile-changes")
    public ApiResponse<Map<String, Object>> propose(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(workplaceService.propose(body, actor));
    }

    @PostMapping("/profile-changes/{id}/decide")
    public ApiResponse<Map<String, Object>> decide(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(workplaceService.decide(id, body, actor));
    }

    @GetMapping("/training")
    public ApiResponse<Map<String, Object>> training(
            @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal UserPrincipal actor) {
        int y = year == null ? java.time.LocalDate.now().getYear() : year;
        return ApiResponse.ok(workplaceService.training(y, actor));
    }

    @PostMapping("/training/courses")
    public ApiResponse<Map<String, Object>> course(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(workplaceService.createCourse(body, actor));
    }

    @PostMapping("/training/enrollments")
    public ApiResponse<Map<String, Object>> enroll(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(workplaceService.enroll(body, actor));
    }

    @PostMapping("/training/enrollments/{id}/done")
    public ApiResponse<Map<String, Object>> done(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(workplaceService.completeEnrollment(id, actor));
    }

    @PostMapping("/attendance/clock")
    public ApiResponse<Map<String, Object>> clock(
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(workplaceService.clock(body == null ? Map.of() : body, actor));
    }
}
