package cn.iocoder.yudao.module.ai.service.invoice;

import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 发票识别运行参数读取服务。
 *
 * <p>优先读取管理后台“系统管理 -> 参数配置”的 infra_config，未配置时回退到 Spring 配置或环境变量。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiInvoiceRecognitionConfigService {

    public static final String CONFIG_KEY_PROVIDER = "ai.invoice.recognition.provider";
    public static final String CONFIG_KEY_MODEL = "ai.invoice.recognition.model";
    public static final String CONFIG_KEY_MAX_OCR_CHARS = "ai.invoice.recognition.max-ocr-chars";
    public static final String CONFIG_KEY_MAX_TOKENS = "ai.invoice.recognition.max-tokens";
    public static final String CONFIG_KEY_FALLBACK_TO_MOCK = "ai.invoice.recognition.fallback-to-mock";

    private static final String PROVIDER_MOCK = "mock";
    private static final String PROVIDER_MODEL = "model";
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(PROVIDER_MOCK, PROVIDER_MODEL, "llm",
            "openai-compatible");

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final AiProperties aiProperties;

    public String getRecognitionProvider() {
        String configValue = normalizeProvider(queryConfigValue(CONFIG_KEY_PROVIDER));
        if (configValue != null) {
            return configValue;
        }
        String propertyValue = aiProperties.getInvoice() == null ? null : aiProperties.getInvoice().getRecognitionProvider();
        String normalizedPropertyValue = normalizeProvider(propertyValue);
        return normalizedPropertyValue == null ? PROVIDER_MOCK : normalizedPropertyValue;
    }

    public String getRecognitionModel() {
        String configValue = trimToNull(queryConfigValue(CONFIG_KEY_MODEL));
        if (configValue != null) {
            return configValue;
        }
        return aiProperties.getInvoice() == null ? null : trimToNull(aiProperties.getInvoice().getRecognitionModel());
    }

    public Integer getRecognitionMaxOcrChars() {
        Integer configValue = positiveInteger(queryConfigValue(CONFIG_KEY_MAX_OCR_CHARS));
        if (configValue != null) {
            return configValue;
        }
        return aiProperties.getInvoice() == null ? null : aiProperties.getInvoice().getRecognitionMaxOcrChars();
    }

    public Integer getRecognitionMaxTokens() {
        Integer configValue = positiveInteger(queryConfigValue(CONFIG_KEY_MAX_TOKENS));
        if (configValue != null) {
            return configValue;
        }
        return aiProperties.getInvoice() == null ? null : aiProperties.getInvoice().getRecognitionMaxTokens();
    }

    public boolean isRecognitionFallbackToMock() {
        Boolean configValue = booleanValue(queryConfigValue(CONFIG_KEY_FALLBACK_TO_MOCK));
        if (configValue != null) {
            return configValue;
        }
        Boolean propertyValue = aiProperties.getInvoice() == null ? null
                : aiProperties.getInvoice().getRecognitionFallbackToMock();
        return propertyValue == null || propertyValue;
    }

    private String queryConfigValue(String key) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT `value`
                    FROM `infra_config`
                    WHERE `key` = ? AND `deleted` = 0
                    LIMIT 1
                    """, String.class, key);
        } catch (DataAccessException ex) {
            log.debug("Invoice recognition system config unavailable, fallback to ai.invoice. key={}", key);
            return null;
        }
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PROVIDERS.contains(normalized)) {
            log.warn("Unsupported invoice recognition provider ignored. key={}, value={}", CONFIG_KEY_PROVIDER,
                    provider);
            return null;
        }
        return normalized;
    }

    private Integer positiveInteger(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(text);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean booleanValue(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return Boolean.parseBoolean(text);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
