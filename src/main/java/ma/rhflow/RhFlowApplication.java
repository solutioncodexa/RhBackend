package ma.rhflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import ma.rhflow.config.JwtProperties;
import ma.rhflow.tenant.TenantAwareJpaRepository;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(JwtProperties.class)
@EnableJpaRepositories(basePackages = "ma.rhflow.domain.repository", repositoryBaseClass = TenantAwareJpaRepository.class)
public class RhFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(RhFlowApplication.class, args);
    }
}
