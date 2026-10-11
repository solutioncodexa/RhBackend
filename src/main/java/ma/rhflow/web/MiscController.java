package ma.rhflow.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.ApiResponse;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.service.DashboardService;
import ma.rhflow.service.ReportService;
import ma.rhflow.service.SettingsService;

@RestController
@RequiredArgsConstructor
public class MiscController {

    private final DashboardService dashboardService;
    private final ReportService reportService;
    private final SettingsService settingsService;
    private final ma.rhflow.service.FileStorageService fileStorageService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(@AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(dashboardService.dashboard(actor));
    }

    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(
            @RequestParam String q,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(dashboardService.search(q, actor));
    }

    @GetMapping({"/settings", "/company"})
    public ApiResponse<Map<String, Object>> settings() {
        return ApiResponse.ok(settingsService.get());
    }

    @PutMapping({"/settings", "/company"})
    public ApiResponse<Map<String, Object>> updateSettings(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(settingsService.update(body, actor));
    }

    @PostMapping(value = "/settings/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadLogo(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(settingsService.uploadLogo(file, actor));
    }

    @GetMapping("/settings/logo")
    public ResponseEntity<byte[]> logo() {
        SettingsService.ImageFile image = settingsService.logo();
        return ResponseEntity.ok().contentType(image.contentType()).body(image.bytes());
    }

    @GetMapping("/reports/{kind}")
    public ResponseEntity<byte[]> report(
            @PathVariable String kind,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @AuthenticationPrincipal UserPrincipal actor) {
        return reportService.export(kind, actor, year, month);
    }

    @GetMapping("/reports/attendance.csv")
    public ResponseEntity<byte[]> attendanceCsv(@AuthenticationPrincipal UserPrincipal actor) {
        return reportService.export("attendance.csv", actor, null, null);
    }

    @GetMapping("/files/signed")
    public ResponseEntity<byte[]> signed(@RequestParam String token) {
        byte[] bytes = fileStorageService.readSigned(token);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }
}
