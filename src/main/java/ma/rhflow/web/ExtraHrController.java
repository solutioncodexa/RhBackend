package ma.rhflow.web;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import ma.rhflow.domain.entity.HrRecord;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.domain.enums.PermissionCodes;
import ma.rhflow.service.ExtraHrService;
import ma.rhflow.service.LegalFileService;
import ma.rhflow.service.PermissionService;

@RestController
@RequiredArgsConstructor
public class ExtraHrController {

    private final ExtraHrService extraHrService;
    private final LegalFileService legalFileService;
    private final PermissionService permissionService;

    @GetMapping("/ops/status")
    public ApiResponse<Map<String, Object>> ops() {
        return ApiResponse.ok(extraHrService.opsStatus());
    }

    @GetMapping("/establishments")
    public ApiResponse<Map<String, Object>> establishments(@AuthenticationPrincipal UserPrincipal actor) {
        return page(HrRecord.ESTABLISHMENT, actor);
    }

    @PostMapping("/establishments")
    public ApiResponse<Map<String, Object>> createEstablishment(
            @RequestBody Map<String, Object> body, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.create(HrRecord.ESTABLISHMENT, body, actor));
    }

    @PutMapping("/establishments/{id}")
    public ApiResponse<Map<String, Object>> updateEstablishment(
            @PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.update(HrRecord.ESTABLISHMENT, id, body, actor));
    }

    @DeleteMapping("/establishments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEstablishment(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        extraHrService.delete(HrRecord.ESTABLISHMENT, id, actor);
    }

    @GetMapping("/shifts")
    public ApiResponse<Map<String, Object>> shifts(@AuthenticationPrincipal UserPrincipal actor) {
        return page(HrRecord.SHIFT, actor);
    }

    @PostMapping("/shifts")
    public ApiResponse<Map<String, Object>> createShift(
            @RequestBody Map<String, Object> body, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.create(HrRecord.SHIFT, body, actor));
    }

    @PutMapping("/shifts/{id}")
    public ApiResponse<Map<String, Object>> updateShift(
            @PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.update(HrRecord.SHIFT, id, body, actor));
    }

    @DeleteMapping("/shifts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShift(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        extraHrService.delete(HrRecord.SHIFT, id, actor);
    }

    @GetMapping("/shift-assignments")
    public ApiResponse<Map<String, Object>> assignments(@AuthenticationPrincipal UserPrincipal actor) {
        return page(HrRecord.SHIFT_ASSIGNMENT, actor);
    }

    @PostMapping("/shift-assignments")
    public ApiResponse<Map<String, Object>> createAssignment(
            @RequestBody Map<String, Object> body, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.create(HrRecord.SHIFT_ASSIGNMENT, body, actor));
    }

    @DeleteMapping("/shift-assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssignment(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        extraHrService.delete(HrRecord.SHIFT_ASSIGNMENT, id, actor);
    }

    @GetMapping("/loans")
    public ApiResponse<Map<String, Object>> loans(@AuthenticationPrincipal UserPrincipal actor) {
        return page(HrRecord.LOAN, actor);
    }

    @PostMapping("/loans")
    public ApiResponse<Map<String, Object>> createLoan(
            @RequestBody Map<String, Object> body, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.create(HrRecord.LOAN, body, actor));
    }

    @PutMapping("/loans/{id}")
    public ApiResponse<Map<String, Object>> updateLoan(
            @PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.update(HrRecord.LOAN, id, body, actor));
    }

    @DeleteMapping("/loans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLoan(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        extraHrService.delete(HrRecord.LOAN, id, actor);
    }

    @GetMapping("/assets")
    public ApiResponse<Map<String, Object>> assets(@AuthenticationPrincipal UserPrincipal actor) {
        return page(HrRecord.ASSET, actor);
    }

    @PostMapping("/assets")
    public ApiResponse<Map<String, Object>> createAsset(
            @RequestBody Map<String, Object> body, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.create(HrRecord.ASSET, body, actor));
    }

    @PutMapping("/assets/{id}")
    public ApiResponse<Map<String, Object>> updateAsset(
            @PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.update(HrRecord.ASSET, id, body, actor));
    }

    @DeleteMapping("/assets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAsset(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        extraHrService.delete(HrRecord.ASSET, id, actor);
    }

    @GetMapping("/disciplinary")
    public ApiResponse<Map<String, Object>> disciplinary(@AuthenticationPrincipal UserPrincipal actor) {
        return page(HrRecord.DISCIPLINARY, actor);
    }

    @PostMapping("/disciplinary")
    public ApiResponse<Map<String, Object>> createCase(
            @RequestBody Map<String, Object> body, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.create(HrRecord.DISCIPLINARY, body, actor));
    }

    @PutMapping("/disciplinary/{id}")
    public ApiResponse<Map<String, Object>> updateCase(
            @PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.update(HrRecord.DISCIPLINARY, id, body, actor));
    }

    @PostMapping("/documents/{id}/sign")
    public ApiResponse<Map<String, Object>> sign(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.signDocument(id, actor));
    }

    @PostMapping("/payroll/simulate")
    public ApiResponse<Map<String, Object>> simulate(
            @RequestBody Map<String, Object> body, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.simulate(body, actor));
    }

    @GetMapping("/payroll/ir-regularize")
    public ApiResponse<List<Map<String, Object>>> ir(
            @RequestParam(required = false) Integer year, @AuthenticationPrincipal UserPrincipal actor) {
        int y = year == null ? java.time.LocalDate.now().getYear() : year;
        return ApiResponse.ok(extraHrService.irRegularize(y, actor));
    }

    @GetMapping("/payroll/settlement/{employeeId}")
    public ApiResponse<Map<String, Object>> settlement(
            @PathVariable Long employeeId, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(extraHrService.settlement(employeeId, actor));
    }

    @GetMapping("/reports/damancom-edi")
    public ResponseEntity<byte[]> damancom(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @AuthenticationPrincipal UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
        int y = year == null ? java.time.LocalDate.now().getYear() : year;
        int m = month == null ? java.time.LocalDate.now().getMonthValue() : month;
        return legalFileService.damancomEdi(y, m);
    }

    @GetMapping("/reports/9421-edi")
    public ResponseEntity<byte[]> edi9421(
            @RequestParam(required = false) Integer year, @AuthenticationPrincipal UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
        int y = year == null ? java.time.LocalDate.now().getYear() : year;
        return legalFileService.etat9421Edi(y);
    }

    @GetMapping("/reports/virement-simt")
    public ResponseEntity<byte[]> simt(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @AuthenticationPrincipal UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
        int y = year == null ? java.time.LocalDate.now().getYear() : year;
        int m = month == null ? java.time.LocalDate.now().getMonthValue() : month;
        return legalFileService.virementSimt(y, m);
    }

    @GetMapping("/reports/cimr")
    public ResponseEntity<byte[]> cimr(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @AuthenticationPrincipal UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
        int y = year == null ? java.time.LocalDate.now().getYear() : year;
        int m = month == null ? java.time.LocalDate.now().getMonthValue() : month;
        return legalFileService.cimr(y, m);
    }

    private ApiResponse<Map<String, Object>> page(String kind, UserPrincipal actor) {
        List<Map<String, Object>> rows = extraHrService.list(kind, actor);
        int size = Math.max(rows.size(), 1);
        return ApiResponse.ok(CatalogController.pageMap(new PageImpl<>(rows, PageRequest.of(0, size), rows.size())));
    }
}
