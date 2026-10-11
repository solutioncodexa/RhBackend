package ma.rhflow.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.ApiResponse;
import ma.rhflow.domain.entity.HrDocument;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.service.CatalogService;
import ma.rhflow.service.FileStorageService;
import ma.rhflow.service.FileStorageService.StoredFile;

@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final FileStorageService fileStorageService;

    @PostMapping("/onboarding/launch")
    public ApiResponse<Map<String, Object>> launchOnboarding(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(catalogService.launchChecklist("onboarding", body, actor));
    }

    @PostMapping("/offboarding/launch")
    public ApiResponse<Map<String, Object>> launchOffboarding(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(catalogService.launchChecklist("offboarding", body, actor));
    }

    @PostMapping(value = "/employees/onboard", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> onboard(
            @RequestPart("payload") Map<String, Object> payload,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserPrincipal actor) {
        StoredFile stored = file == null || file.isEmpty() ? null : fileStorageService.store(file);
        return ApiResponse.ok(catalogService.hire(payload, stored, actor));
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "true") boolean visibleToEmployee,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal actor) {
        StoredFile stored = fileStorageService.store(file);
        return ApiResponse.ok(catalogService.attachDocument(
                employeeId, contractId, category, name, visibleToEmployee, stored, actor));
    }

    @GetMapping("/employee-requests/{id}/pdf")
    public ResponseEntity<byte[]> requestPdf(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return pdf(catalogService.requestPdf(id, actor), "attestation-" + id + ".pdf");
    }

    @GetMapping("/contracts/{id}/pdf")
    public ResponseEntity<byte[]> contractPdf(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return pdf(catalogService.contractPdf(id, actor), "contrat-" + id + ".pdf");
    }

    @PostMapping(value = "/employees/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadPhoto(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal actor) {
        if (file == null || file.isEmpty() || file.getContentType() == null || !file.getContentType().toLowerCase().startsWith("image/")) {
            throw ma.rhflow.common.exception.BusinessException.badRequest("La photo doit être une image");
        }
        StoredFile stored = fileStorageService.store(file, 800);
        return ApiResponse.ok(catalogService.setPhoto(id, stored, actor));
    }

    @GetMapping("/employees/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        ma.rhflow.service.SettingsService.ImageFile image = catalogService.photo(id, actor);
        return ResponseEntity.ok().contentType(image.contentType()).body(image.bytes());
    }

    @PostMapping("/employees/{id}/documents/generate")
    public ApiResponse<Map<String, Object>> generateDocument(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(catalogService.generateEmployeeDocument(id, body, actor));
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        HrDocument doc = catalogService.documentMeta(id, actor);
        byte[] bytes = catalogService.documentBytes(id, actor);
        String filename = doc.getOriginalName() == null ? "document" : doc.getOriginalName().replace("\"", "");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        doc.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : doc.getContentType()))
                .body(bytes);
    }

    private ResponseEntity<byte[]> pdf(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @GetMapping({
            "/employees", "/departments", "/positions", "/contracts", "/attendance", "/working-hours",
            "/leave-types", "/leave-balances", "/documents", "/employee-requests", "/announcements",
            "/notifications", "/expenses", "/business-trips", "/performance-reviews", "/objectives",
            "/job-offers", "/candidates", "/onboarding", "/offboarding", "/holidays", "/audit-logs", "/payslips"
    })
    public ApiResponse<Map<String, Object>> list(
            WebRequest request,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam Map<String, String> params,
            @AuthenticationPrincipal UserPrincipal actor) {
        String resource = resource(request);
        Page<Map<String, Object>> result = catalogService.list(resource, params, page, size, actor);
        return ApiResponse.ok(pageMap(result));
    }

    @GetMapping({
            "/employees/{id}", "/departments/{id}", "/positions/{id}", "/contracts/{id}", "/attendance/{id}",
            "/working-hours/{id}", "/leave-types/{id}", "/leave-balances/{id}", "/documents/{id}",
            "/employee-requests/{id}", "/announcements/{id}", "/notifications/{id}", "/expenses/{id}",
            "/business-trips/{id}", "/performance-reviews/{id}", "/objectives/{id}", "/job-offers/{id}",
            "/candidates/{id}", "/onboarding/{id}", "/offboarding/{id}", "/holidays/{id}", "/payslips/{id}"
    })
    public ApiResponse<Map<String, Object>> get(
            WebRequest request,
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal actor) {
        String resource = resource(request).replace("/" + id, "");
        return ApiResponse.ok(catalogService.get(resource, id, actor));
    }

    @PostMapping({
            "/employees", "/departments", "/positions", "/contracts", "/attendance", "/working-hours",
            "/leave-types", "/leave-balances", "/documents", "/employee-requests", "/announcements",
            "/expenses", "/business-trips", "/performance-reviews", "/objectives",
            "/job-offers", "/candidates", "/onboarding", "/offboarding", "/holidays"
    })
    public ApiResponse<Map<String, Object>> create(
            WebRequest request,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(catalogService.create(resource(request), body, actor));
    }

    @PutMapping({
            "/employees/{id}", "/departments/{id}", "/positions/{id}", "/contracts/{id}", "/attendance/{id}",
            "/working-hours/{id}", "/leave-types/{id}", "/leave-balances/{id}", "/documents/{id}",
            "/employee-requests/{id}", "/announcements/{id}", "/expenses/{id}",
            "/business-trips/{id}", "/performance-reviews/{id}", "/objectives/{id}", "/job-offers/{id}",
            "/candidates/{id}", "/onboarding/{id}", "/offboarding/{id}", "/holidays/{id}"
    })
    public ApiResponse<Map<String, Object>> update(
            WebRequest request,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        String resource = resource(request).replace("/" + id, "");
        return ApiResponse.ok(catalogService.update(resource, id, body, actor));
    }

    @DeleteMapping({
            "/employees/{id}", "/departments/{id}", "/positions/{id}", "/contracts/{id}", "/attendance/{id}",
            "/working-hours/{id}", "/leave-types/{id}", "/leave-balances/{id}", "/documents/{id}",
            "/employee-requests/{id}", "/announcements/{id}", "/expenses/{id}",
            "/business-trips/{id}", "/performance-reviews/{id}", "/objectives/{id}", "/job-offers/{id}",
            "/candidates/{id}", "/onboarding/{id}", "/offboarding/{id}", "/holidays/{id}"
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            WebRequest request,
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal actor) {
        String resource = resource(request).replace("/" + id, "");
        catalogService.delete(resource, id, actor);
    }

    @PostMapping("/employee-requests/{id}/process")
    public ApiResponse<Map<String, Object>> processRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(catalogService.processRequest(id, body == null ? Map.of() : body, actor));
    }

    @PostMapping("/notifications/{id}/read")
    public ApiResponse<Map<String, Object>> readNotif(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(catalogService.markNotificationRead(id, actor));
    }

    @PostMapping("/notifications/read-all")
    public ApiResponse<Map<String, Object>> readAll(@AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(catalogService.markAllNotificationsRead(actor));
    }

    private String resource(WebRequest request) {
        String uri = request.getDescription(false).replace("uri=", "");
        int q = uri.indexOf('?');
        if (q >= 0) {
            uri = uri.substring(0, q);
        }
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }
        int slash = uri.indexOf('/');
        return slash < 0 ? uri : uri.substring(0, slash);
    }

    static Map<String, Object> pageMap(Page<?> page) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("content", page.getContent());
        m.put("totalElements", page.getTotalElements());
        m.put("totalPages", page.getTotalPages());
        m.put("number", page.getNumber());
        m.put("size", page.getSize());
        m.put("page", page.getNumber());
        return m;
    }
}
