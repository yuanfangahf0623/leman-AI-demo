package cn.iocoder.yudao.module.ai.framework.meeting;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.teams.meeting")
public class AiTeamsMeetingProperties {

    private Boolean enabled = false;

    private String graphBaseUrl = "https://graph.microsoft.com/v1.0";

    private String loginBaseUrl = "https://login.microsoftonline.com";

    private String tenantId;

    private String clientId;

    private String clientSecret;

    private String defaultOrganizerUserId;

    private Long defaultKnowledgeBaseId;

    private Boolean defaultChatgptVisible = false;

    private String defaultSensitivityLevel = "NORMAL";

    private Integer syncTop = 50;

    private Integer transcriptMaxChars = 200_000;

    private Integer connectTimeoutSeconds = 10;

    private Integer readTimeoutSeconds = 120;

}
