package ma.rhflow.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import ma.rhflow.domain.entity.HrRecord;
import ma.rhflow.domain.entity.Subscription;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.repository.HrRecordRepository;
import ma.rhflow.domain.repository.SubscriptionRepository;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.tenant.TenantContext;

@Slf4j
@Service
public class WhatsAppService {

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final HrRecordRepository hrRecordRepository;
    private final boolean enabled;
    private final String token;
    private final String graphVersion;
    private final HttpClient http;

    public WhatsAppService(
            TenantRepository tenantRepository,
            SubscriptionRepository subscriptionRepository,
            HrRecordRepository hrRecordRepository,
            @Value("${app.whatsapp.enabled:false}") boolean enabled,
            @Value("${app.whatsapp.token:}") String token,
            @Value("${app.whatsapp.graph-version:v21.0}") String graphVersion) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.hrRecordRepository = hrRecordRepository;
        this.enabled = enabled;
        this.token = token == null ? "" : token.trim();
        this.graphVersion = graphVersion == null || graphVersion.isBlank() ? "v21.0" : graphVersion.trim();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    public void notify(String phone, String text) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        String gate = gate();
        if ("local".equals(gate)) {
            store(phone, text, "LOCAL");
            log.info("WhatsApp journalisé (local) phone={}", mask(phone));
            return;
        }
        if (!"ready".equals(gate)) {
            log.info("WhatsApp non envoyé ({}) phone={}", gate, mask(phone));
            return;
        }
        Tenant tenant = currentTenant();
        String phoneNumberId = tenant == null ? "" : blank(tenant.getWhatsappPhoneNumberId());
        String access = accessToken(tenant);
        String body = "{\"messaging_product\":\"whatsapp\",\"to\":\"" + escape(digits(phone))
                + "\",\"type\":\"text\",\"text\":{\"body\":\"" + escape(text == null ? "" : text) + "\"}}";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.facebook.com/" + graphVersion + "/" + phoneNumberId + "/messages"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + access)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                store(phone, text, "SENT");
                log.info("WhatsApp envoyé phone={}", mask(phone));
            } else {
                store(phone, text, "REFUSED");
                log.warn("WhatsApp refusé ({}) phone={}", response.statusCode(), mask(phone));
            }
        } catch (Exception ex) {
            log.warn("WhatsApp non délivré phone={} : {}", mask(phone), ex.getMessage());
        }
    }

    public Map<String, Object> explain() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("transportEnabled", enabled);
        status.put("tokenConfigured", !token.isBlank());
        status.put("reason", gate());
        return status;
    }

    public String gate() {
        if (!enabled) {
            return "disabled";
        }
        Tenant tenant = currentTenant();
        if (tenant == null || !tenant.isWhatsappEnabled()) {
            return "off";
        }
        if (!planAllows(tenant.getId())) {
            return "plan";
        }
        if (accessToken(tenant).isBlank() || blank(tenant.getWhatsappPhoneNumberId()).isBlank()) {
            return "local";
        }
        return "ready";
    }

    private String accessToken(Tenant tenant) {
        if (tenant != null && !blank(tenant.getWhatsappAccessToken()).isBlank()) {
            return tenant.getWhatsappAccessToken().trim();
        }
        return token;
    }

    private void store(String phone, String text, String status) {
        try {
            HrRecord row = new HrRecord();
            row.setTenantId(TenantContext.getTenantId());
            row.setKind(HrRecord.OUTBOUND);
            row.setTitle(mask(phone));
            row.setCode(digits(phone));
            row.setStatus(status);
            row.setNotes(text);
            row.setPayload("WHATSAPP");
            hrRecordRepository.save(row);
        } catch (Exception ignored) {
            log.debug("outbox WhatsApp non persisté");
        }
    }

    public boolean planAllows(Long tenantId) {
        if (tenantId == null) {
            return false;
        }
        return subscriptionRepository.findByTenantId(tenantId).map(Subscription::isFeatureWhatsapp).orElse(false);
    }

    private Tenant currentTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return null;
        }
        return tenantRepository.findById(tenantId).orElse(null);
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static String digits(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }

    private static String mask(String phone) {
        String raw = digits(phone);
        if (raw.length() < 4) {
            return "****";
        }
        return "****" + raw.substring(raw.length() - 4);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
