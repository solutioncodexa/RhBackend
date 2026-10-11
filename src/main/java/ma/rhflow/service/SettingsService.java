package ma.rhflow.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.enums.PermissionCodes;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.tenant.TenantContext;
import ma.rhflow.util.JsonMaps;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final TenantRepository tenantRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final WhatsAppService whatsAppService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public Map<String, Object> get() {
        Tenant t = tenant();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("legalName", t.getLegalName());
        m.put("ice", t.getIce());
        m.put("taxId", t.getTaxId());
        m.put("rc", t.getRc());
        m.put("cnssAffiliation", t.getCnssAffiliation());
        m.put("email", t.getEmail());
        m.put("phone", t.getPhone());
        m.put("city", t.getCity());
        m.put("address", t.getAddress());
        m.put("website", t.getWebsite());
        m.put("sector", t.getSector());
        m.put("currency", t.getCurrency());
        m.put("hasLogo", t.getLogoPath() != null && !t.getLogoPath().isBlank());
        m.put("plan", t.getPlan() == null ? null : t.getPlan().name());
        m.put("maxEmployees", t.getPlan() == null ? 25 : t.getPlan().maxEmployees());
        m.put("maxUsers", t.getPlan() == null ? 10 : t.getPlan().maxUsers());
        m.put("featureOllama", t.isFeatureOllama());
        m.put("whatsappPhoneNumberId", t.getWhatsappPhoneNumberId());
        m.put("whatsappBusinessAccountId", t.getWhatsappBusinessAccountId());
        m.put("whatsappAccessToken", "");
        m.put("whatsappPhone", t.getWhatsappPhoneNumberId());
        m.put("whatsappToken", "");
        m.put("whatsappEnabled", String.valueOf(t.isWhatsappEnabled()));
        m.put("featureWhatsapp", whatsAppService.planAllows(t.getId()));
        m.putAll(prefixedWhatsapp(whatsAppService.explain()));
        m.put("trialEndsAt", t.getTrialEndsAt());
        m.put("cimrEmployeeRate", t.getCimrEmployeeRate());
        m.put("cimrEmployerRate", t.getCimrEmployerRate());
        m.put("mutualEmployeeRate", t.getMutualEmployeeRate());
        m.put("mutualEmployerRate", t.getMutualEmployerRate());
        return m;
    }

    @Transactional
    public Map<String, Object> update(Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
        Tenant t = tenant();
        if (body.containsKey("name")) t.setName(JsonMaps.str(body, "name"));
        if (body.containsKey("legalName")) t.setLegalName(JsonMaps.str(body, "legalName"));
        if (body.containsKey("ice")) {
            String ice = JsonMaps.str(body, "ice");
            if (ice != null && !ice.isBlank()) {
                boolean prev = TenantContext.isBypass();
                try {
                    TenantContext.setBypass(true);
                    boolean taken = tenantRepository.findAll().stream()
                            .anyMatch(o -> !o.getId().equals(t.getId()) && ice.equals(o.getIce()));
                    if (taken) {
                        throw BusinessException.conflict("ICE déjà utilisé");
                    }
                } finally {
                    TenantContext.setBypass(prev);
                }
            }
            t.setIce(ice);
        }
        if (body.containsKey("taxId")) t.setTaxId(JsonMaps.str(body, "taxId"));
        if (body.containsKey("rc")) t.setRc(JsonMaps.str(body, "rc"));
        if (body.containsKey("cnssAffiliation")) t.setCnssAffiliation(JsonMaps.str(body, "cnssAffiliation"));
        if (body.containsKey("email")) t.setEmail(JsonMaps.str(body, "email"));
        if (body.containsKey("phone")) t.setPhone(JsonMaps.str(body, "phone"));
        if (body.containsKey("city")) t.setCity(JsonMaps.str(body, "city"));
        if (body.containsKey("address")) t.setAddress(JsonMaps.str(body, "address"));
        if (body.containsKey("website")) t.setWebsite(JsonMaps.str(body, "website"));
        if (body.containsKey("sector")) t.setSector(JsonMaps.str(body, "sector"));
        if (body.containsKey("currency")) t.setCurrency(JsonMaps.str(body, "currency"));
        if (body.containsKey("whatsappPhoneNumberId") || body.containsKey("whatsappPhone")) {
            t.setWhatsappPhoneNumberId(JsonMaps.str(body, "whatsappPhoneNumberId", "whatsappPhone"));
        }
        if (body.containsKey("whatsappBusinessAccountId")) {
            t.setWhatsappBusinessAccountId(JsonMaps.str(body, "whatsappBusinessAccountId"));
        }
        t.setWhatsappAccessToken(null);
        if (body.containsKey("whatsappEnabled")) {
            boolean turnOn = Boolean.TRUE.equals(JsonMaps.bool(body, "whatsappEnabled"));
            if (turnOn && !whatsAppService.planAllows(t.getId())) {
                throw BusinessException.badRequest("WhatsApp est réservé aux plans Pro et Entreprise");
            }
            t.setWhatsappEnabled(turnOn);
        }
        if (body.containsKey("cimrEmployeeRate")) t.setCimrEmployeeRate(JsonMaps.dec(body, "cimrEmployeeRate"));
        if (body.containsKey("cimrEmployerRate")) t.setCimrEmployerRate(JsonMaps.dec(body, "cimrEmployerRate"));
        if (body.containsKey("mutualEmployeeRate")) t.setMutualEmployeeRate(JsonMaps.dec(body, "mutualEmployeeRate"));
        if (body.containsKey("mutualEmployerRate")) t.setMutualEmployerRate(JsonMaps.dec(body, "mutualEmployerRate"));
        tenantRepository.save(t);
        auditService.log(actor, "UPDATE", "settings", t.getId(), "company");
        return get();
    }

    @Transactional
    public Map<String, Object> uploadLogo(MultipartFile file, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.SETTINGS_MANAGE);
        if (file == null || file.isEmpty() || file.getContentType() == null || !file.getContentType().toLowerCase().startsWith("image/")) {
            throw BusinessException.badRequest("Le logo doit être une image");
        }
        Tenant t = tenant();
        FileStorageService.StoredFile stored = fileStorageService.store(file, 512);
        String previous = t.getLogoPath();
        t.setLogoPath(stored.storedName());
        tenantRepository.save(t);
        if (previous != null && !previous.equals(stored.storedName())) {
            fileStorageService.delete(previous);
        }
        auditService.log(actor, "UPDATE", "settings", t.getId(), "logo");
        return get();
    }

    @Transactional(readOnly = true)
    public ImageFile logo() {
        Tenant t = tenant();
        if (t.getLogoPath() == null || t.getLogoPath().isBlank()) {
            throw BusinessException.notFound("Aucun logo");
        }
        return new ImageFile(fileStorageService.read(t.getLogoPath()), media(t.getLogoPath()));
    }

    public static MediaType media(String path) {
        if (path != null && path.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (path != null && path.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

    public record ImageFile(byte[] bytes, MediaType contentType) {
    }

    private Map<String, Object> prefixedWhatsapp(Map<String, Object> explain) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("whatsappTransportEnabled", explain.get("transportEnabled"));
        out.put("whatsappTokenConfigured", explain.get("tokenConfigured"));
        out.put("whatsappReason", explain.get("reason"));
        return out;
    }

    private Tenant tenant() {
        Long id = TenantContext.requireTenantId();
        return tenantRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Société introuvable"));
    }
}
