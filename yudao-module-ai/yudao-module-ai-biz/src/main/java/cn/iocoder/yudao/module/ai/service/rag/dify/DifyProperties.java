package cn.iocoder.yudao.module.ai.service.rag.dify;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.dify")
public class DifyProperties {
    private String baseUrl;
    private String apiKey;
    private String datasetId;
    private Long tenantId;
    private Long knowledgeBaseId;
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 180;
}
