package ma.rhflow.assistant.web;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ma.rhflow.assistant.service.AssistantOrchestrator;
import ma.rhflow.common.ApiResponse;
import ma.rhflow.security.UserPrincipal;

@RestController
@RequestMapping("/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantOrchestrator orchestrator;

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health(@AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(orchestrator.health(actor));
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(orchestrator.chat(body, actor));
    }

    @PostMapping("/confirm")
    public ApiResponse<Map<String, Object>> confirm(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(orchestrator.confirm(body, actor));
    }
}
