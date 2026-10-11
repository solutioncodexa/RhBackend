package ma.rhflow.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.rhflow.domain.entity.PlatformPlanDefault;
import ma.rhflow.domain.entity.Subscription;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.enums.SubscriptionPlan;
import ma.rhflow.domain.repository.PlatformPlanDefaultRepository;
import ma.rhflow.domain.repository.SubscriptionRepository;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.tenant.TenantContext;

@Component
@Order(50)
@RequiredArgsConstructor
public class InfraActivator implements ApplicationRunner {

    private final PlatformPlanDefaultRepository planDefaultRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean prev = TenantContext.isBypass();
        try {
            TenantContext.setBypass(true);
            for (PlatformPlanDefault row : planDefaultRepository.findAll()) {
                if (row.getPlan() != SubscriptionPlan.ESSENTIEL && !row.isFeatureWhatsapp()) {
                    row.setFeatureWhatsapp(true);
                    planDefaultRepository.save(row);
                }
            }
            for (Subscription s : subscriptionRepository.findAll()) {
                if (s.getPlan() != SubscriptionPlan.ESSENTIEL && !s.isFeatureWhatsapp()) {
                    s.setFeatureWhatsapp(true);
                    subscriptionRepository.save(s);
                }
            }
            for (Tenant t : tenantRepository.findAll()) {
                if (t.getPlan() != SubscriptionPlan.ESSENTIEL && !t.isWhatsappEnabled()) {
                    t.setWhatsappEnabled(true);
                    tenantRepository.save(t);
                }
            }
        } finally {
            TenantContext.setBypass(prev);
        }
    }
}
