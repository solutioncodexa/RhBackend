package ma.rhflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;

@Configuration
public class MinioConfig {

    @Bean
    @ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
    public MinioClient minioClient(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.access}") String access,
            @Value("${app.minio.secret}") String secret) {
        return MinioClient.builder().endpoint(endpoint).credentials(access, secret).build();
    }
}
