package ma.rhflow.web;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.ApiResponse;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.service.PayrollService;

@RestController
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    @GetMapping("/payroll/periods")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(CatalogController.pageMap(payrollService.listPeriods(page, size, actor)));
    }

    @GetMapping("/payroll/periods/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(payrollService.getPeriod(id, actor));
    }

    @PostMapping("/payroll/periods")
    public ApiResponse<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(payrollService.createPeriod(body, actor));
    }

    @GetMapping("/payroll/periods/{id}/elements")
    public ApiResponse<Map<String, Object>> elements(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(payrollService.elements(id, actor));
    }

    @PostMapping("/payroll/items")
    public ApiResponse<Map<String, Object>> addItem(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(payrollService.addItem(body, actor));
    }

    @DeleteMapping("/payroll/items/{id}")
    public ApiResponse<Map<String, Object>> deleteItem(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(payrollService.deleteItem(id, actor));
    }

    @PostMapping("/payroll/periods/{id}/calculate")
    public ApiResponse<Map<String, Object>> calculate(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(payrollService.calculate(id, body, actor));
    }

    @PostMapping("/payroll/periods/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(payrollService.approve(id, actor));
    }

    @PostMapping("/payroll/periods/{id}/paid")
    public ApiResponse<Map<String, Object>> paid(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(payrollService.markPaid(id, actor));
    }

    @PostMapping("/payroll/periods/{id}/lock")
    public ApiResponse<Map<String, Object>> lock(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(payrollService.lock(id, actor));
    }

    @PostMapping("/payroll/periods/{id}/credit")
    public ApiResponse<Map<String, Object>> credit(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(payrollService.creditNote(id, body, actor));
    }

    @GetMapping("/payslips/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        byte[] bytes = payrollService.pdf(id, actor);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"bulletin-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @GetMapping("/payslips/{id}/virement")
    public ResponseEntity<byte[]> virement(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        byte[] bytes = payrollService.virementPdf(id, actor);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"virement-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @PostMapping("/payslips/{id}/transfer")
    public ApiResponse<Map<String, Object>> transfer(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(payrollService.recordTransfer(id, body == null ? Map.of() : body, actor));
    }
}
