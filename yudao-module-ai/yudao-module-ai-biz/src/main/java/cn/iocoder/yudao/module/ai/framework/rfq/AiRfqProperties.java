package cn.iocoder.yudao.module.ai.framework.rfq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * RFQ email automation configuration.
 */
@Data
@ConfigurationProperties(prefix = "ai.rfq")
public class AiRfqProperties {

    private EmailProperties email = new EmailProperties();

    private HermesProperties hermes = new HermesProperties();

    private ClassificationProperties classification = new ClassificationProperties();

    private DetectionProperties detection = new DetectionProperties();

    private SecurityProperties security = new SecurityProperties();

    @Data
    public static class EmailProperties {

        /**
         * The existing job module should call EmailSyncScheduler only when this switch is enabled.
         */
        private Boolean enabled = false;

        /**
         * Kept as configuration metadata for the external scheduler.
         */
        private Long syncIntervalMillis = 30_000L;

        private Long syncInitialDelayMillis = 5_000L;

        private String defaultFolder = "INBOX";

        private Integer connectTimeoutMillis = 10_000;

        private Integer readTimeoutMillis = 60_000;

        private Integer maxMessagesPerPoll = 20;

        private List<MailboxProperties> mailboxes = new ArrayList<>();

    }

    @Data
    public static class MailboxProperties {

        private Boolean enabled = true;

        /**
         * Business account key used by sync state. It should be stable and non-secret.
         */
        private String account;

        private Long tenantId = 0L;

        private String host;

        private Integer port = 993;

        private String username;

        /**
         * NetEase Enterprise Mail authorization code / app password.
         */
        private String password;

        private String folder;

        private Integer maxMessagesPerPoll;

    }

    @Data
    public static class HermesProperties {

        /**
         * Optional model override. Empty means using the global ai.model.chat-model.
         */
        private String model;

        /**
         * Externally maintained Hermes prompt. Empty falls back to a minimal strict JSON guardrail.
         */
        private String systemPrompt;

        private Integer maxInputChars = 24_000;

        private Integer maxTokens = 2_000;

    }

    @Data
    public static class ClassificationProperties {

        /**
         * Optional model override. Empty means using the global ai.model.chat-model.
         */
        private String model;

        /**
         * Optional classifier prompt override.
         */
        private String systemPrompt;

        private Integer maxInputChars = 24_000;

        private Integer maxTokens = 800;

    }

    @Data
    public static class DetectionProperties {

        /**
         * Internal email domains. A sender from these domains is not treated as an external RFQ.
         */
        private List<String> internalEmailDomains = new ArrayList<>(List.of("leman-tech.com", "lecho-gmbh.de"));

        /**
         * Internal sender names, aliases or email addresses.
         */
        private List<String> internalSenderKeywords = new ArrayList<>(List.of("Fang Yuan", "yuanf"));

        /**
         * Internal company names. These are checked against structured Hermes customer output before creating RFQ.
         */
        private List<String> internalCompanyKeywords = new ArrayList<>(List.of(
                "leman-tech", "Leman Tech", "理文科技", "理文科技（山东）股份有限公司", "LECHO GmbH"));

        /**
         * Explicit buying / quotation intent signals required before Hermes is called.
         */
        private List<String> intentKeywords = new ArrayList<>(List.of(
                "rfq",
                "request for quote",
                "request for quotation",
                "quotation request",
                "quote request",
                "please quote",
                "please provide a quote",
                "please send a quote",
                "can you quote",
                "need a quote",
                "looking for quotation",
                "pricing request",
                "price request",
                "request pricing",
                "please provide pricing",
                "pricing",
                "cost estimate",
                "quotation",
                "quotation for",
                "quote",
                "quote for",
                "offer",
                "price consultation",
                "design change study",
                "hmg study",
                "询价",
                "询盘",
                "报价",
                "请报价",
                "报价单",
                "询价单",
                "采购询价"
        ));

    }

    @Data
    public static class SecurityProperties {

        /**
         * Secret used to encrypt/decrypt IMAP authorization passwords in DB.
         * Configure through RFQ_PASSWORD_KEY or a config center, never hardcode it.
         */
        private String passwordKey;

    }

}
