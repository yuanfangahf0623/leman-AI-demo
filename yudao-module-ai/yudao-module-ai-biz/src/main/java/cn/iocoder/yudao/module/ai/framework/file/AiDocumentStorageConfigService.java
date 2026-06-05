package cn.iocoder.yudao.module.ai.framework.file;

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
 * Resolves document storage type from admin system config first, then application properties.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiDocumentStorageConfigService {

    public static final String CONFIG_KEY_DOCUMENT_STORAGE_TYPE = "AI_DOCUMENT_STORAGE_TYPE";
    public static final String STORAGE_TYPE_LOCAL = "local";
    public static final String STORAGE_TYPE_MINIO = "minio";

    private static final Set<String> SUPPORTED_STORAGE_TYPES = Set.of(STORAGE_TYPE_LOCAL, STORAGE_TYPE_MINIO);

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final AiProperties aiProperties;

    public String getStorageType() {
        String configValue = normalizeStorageType(queryStorageTypeFromSystemConfig());
        if (configValue != null) {
            return configValue;
        }
        String propertyValue = aiProperties.getDocument() == null ? null : aiProperties.getDocument().getStorageType();
        String normalizedPropertyValue = normalizeStorageType(propertyValue);
        if (normalizedPropertyValue != null) {
            return normalizedPropertyValue;
        }
        return STORAGE_TYPE_MINIO;
    }

    private String queryStorageTypeFromSystemConfig() {
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
                    """, String.class, CONFIG_KEY_DOCUMENT_STORAGE_TYPE);
        } catch (DataAccessException ex) {
            log.debug("Document storage system config unavailable, fallback to ai.document.storage-type. key={}",
                    CONFIG_KEY_DOCUMENT_STORAGE_TYPE);
            return null;
        }
    }

    private String normalizeStorageType(String storageType) {
        if (!StringUtils.hasText(storageType)) {
            return null;
        }
        String normalized = storageType.trim().toLowerCase(Locale.ROOT);
        if ("synology".equals(normalized) || "synology-minio".equals(normalized)
                || "nas".equals(normalized) || "remote".equals(normalized)) {
            normalized = STORAGE_TYPE_MINIO;
        }
        if ("local-file".equals(normalized) || "filesystem".equals(normalized)) {
            normalized = STORAGE_TYPE_LOCAL;
        }
        if (!SUPPORTED_STORAGE_TYPES.contains(normalized)) {
            log.warn("Unsupported document storage type config ignored. key={}, value={}",
                    CONFIG_KEY_DOCUMENT_STORAGE_TYPE, storageType);
            return null;
        }
        return normalized;
    }

}
