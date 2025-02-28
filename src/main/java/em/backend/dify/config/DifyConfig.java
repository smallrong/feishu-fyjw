package em.backend.dify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "dify")
public class DifyConfig {
    private String apiUrl = "https://api.dify.ai/v1";
    private String apiKey = "app-s1Cv9pTc0Ulf9FQUvpd9pHNJ";
} 