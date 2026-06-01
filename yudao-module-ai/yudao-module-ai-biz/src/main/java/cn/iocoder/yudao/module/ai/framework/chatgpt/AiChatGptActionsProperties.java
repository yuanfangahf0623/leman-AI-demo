package cn.iocoder.yudao.module.ai.framework.chatgpt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.chatgpt.actions")
public class AiChatGptActionsProperties {

    /**
     * Whether ChatGPT Actions OpenAPI endpoints are enabled.
     */
    private Boolean enabled = true;

    /**
     * Bearer token read from deployment configuration, for example CHATGPT_ACTION_TOKEN.
     */
    private String token;

    /**
     * Default transcript length limit.
     */
    private Integer maxTranscriptChars = 20_000;

    /**
     * Default RAG topK.
     */
    private Integer defaultTopK = 5;

    /**
     * Whether outbound content should pass through sensitive-data masking.
     */
    private Boolean enableSensitiveMask = true;

}
