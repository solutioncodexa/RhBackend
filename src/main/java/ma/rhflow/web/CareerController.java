package ma.rhflow.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.ApiResponse;
import ma.rhflow.service.ExtraHrService;

@RestController
@RequiredArgsConstructor
public class CareerController {

    private final ExtraHrService extraHrService;

    @GetMapping("/careers/{slug}")
    public ApiResponse<Map<String, Object>> jobs(@PathVariable String slug) {
        return ApiResponse.ok(extraHrService.publicJobs(slug));
    }

    @PostMapping("/careers/{slug}/apply")
    public ApiResponse<Map<String, Object>> apply(
            @PathVariable String slug, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(extraHrService.applyPublic(slug, body));
    }
}
