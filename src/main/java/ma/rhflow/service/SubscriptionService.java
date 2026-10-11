package ma.rhflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.rhflow.domain.entity.PlatformPlanDefault;
import ma.rhflow.domain.entity.Subscription;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.enums.SubscriptionPlan;
import ma.rhflow.domain.repository.PlatformPlanDefaultRepository;
import ma.rhflow.domain.repository.SubscriptionRepository;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlatformPlanDefaultRepository planDefaultRepository;

    @Transactional
    public void ensureDefaults() {
        for (SubscriptionPlan plan : SubscriptionPlan.values()) {
            if (planDefaultRepository.findByPlan(plan).isPresent()) {
                continue;
            }
            PlatformPlanDefault row = new PlatformPlanDefault();
            row.setPlan(plan);
            row.setMaxEmployees(plan.maxEmployees());
            row.setMaxUsers(plan.maxUsers());
            row.setFeatureOllama(plan.ollama());
            row.setFeatureWhatsapp(plan != SubscriptionPlan.ESSENTIEL);
            row.setPriceMad(plan == SubscriptionPlan.ESSENTIEL ? new BigDecimal("490")
                    : plan == SubscriptionPlan.PRO ? new BigDecimal("1290") : new BigDecimal("2490"));
            row.setLabelFr(plan.name());
            row.setLabelAr(plan.name());
            planDefaultRepository.save(row);
        }
    }

    @Transactional
    public Subscription ensureFor(Tenant tenant) {
        ensureDefaults();
        return subscriptionRepository.findByTenantId(tenant.getId()).orElseGet(() -> {
            SubscriptionPlan plan = tenant.getPlan() == null ? SubscriptionPlan.ESSENTIEL : tenant.getPlan();
            PlatformPlanDefault def = planDefaultRepository.findByPlan(plan).orElse(null);
            Subscription s = new Subscription();
            s.setTenantId(tenant.getId());
            s.setPlan(plan);
            s.setExpiresAt(tenant.getTrialEndsAt() == null ? LocalDate.now().plusDays(14) : tenant.getTrialEndsAt());
            s.setMaxEmployees(def == null ? plan.maxEmployees() : def.getMaxEmployees());
            s.setMaxUsers(def == null ? plan.maxUsers() : def.getMaxUsers());
            s.setFeatureOllama(def == null ? plan.ollama() : def.isFeatureOllama());
            s.setFeatureWhatsapp(def != null && def.isFeatureWhatsapp());
            s.setPriceMad(def == null ? BigDecimal.ZERO : def.getPriceMad());
            s.setActive(tenant.isActive());
            return subscriptionRepository.save(s);
        });
    }

    @Transactional
    public Subscription syncPlan(Tenant tenant) {
        Subscription s = ensureFor(tenant);
        s.setPlan(tenant.getPlan());
        PlatformPlanDefault def = planDefaultRepository.findByPlan(tenant.getPlan()).orElse(null);
        if (def != null) {
            s.setMaxEmployees(def.getMaxEmployees());
            s.setMaxUsers(def.getMaxUsers());
            s.setFeatureOllama(def.isFeatureOllama());
            s.setFeatureWhatsapp(def.isFeatureWhatsapp());
            s.setPriceMad(def.getPriceMad());
        }
        s.setActive(tenant.isActive());
        tenant.setFeatureOllama(s.isFeatureOllama());
        return subscriptionRepository.save(s);
    }
}
