package cn.iocoder.yudao.module.ai.service.rag.config;

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
 * Resolves the active RAG engine from admin system config first, then ai.rag.engine.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiRagEngineConfigService {

    public static final String CONFIG_KEY_RAG_ENGINE = "ai.rag.engine";
    public static final String ENGINE_LOCAL = "local";
    public static final String ENGINE_FASTGPT = "fastgpt";

    private static final Set<String> SUPPORTED_ENGINES = Set.of(ENGINE_LOCAL, ENGINE_FASTGPT);

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final AiProperties aiProperties;

    public boolean isFastGptEngine() {
        return ENGINE_FASTGPT.equals(getEngine());
    }

    public String getEngine() {
        String configEngine = normalizeEngine(queryEngineFromSystemConfig());
        if (configEngine != null) {
            return configEngine;
        }
        String propertyEngine = aiProperties.getRag() == null ? null : aiProperties.getRag().getEngine();
        String normalizedPropertyEngine = normalizeEngine(propertyEngine);
        if (normalizedPropertyEngine != null) {
            return normalizedPropertyEngine;
        }
        return ENGINE_FASTGPT;
    }

    private String queryEngineFromSystemConfig() {
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
                    """, String.class, CONFIG_KEY_RAG_ENGINE);
        } catch (DataAccessException ex) {
            log.debug("RAG engine system config unavailable, fallback to ai.rag.engine. key={}",
                    CONFIG_KEY_RAG_ENGINE);
            return null;
        }
    }

    private String normalizeEngine(String engine) {
        if (!StringUtils.hasText(engine)) {
            return null;
        }
        String normalized = engine.trim().toLowerCase(Locale.ROOT);
        if ("fast".equals(normalized)) {
            normalized = ENGINE_FASTGPT;
        }
        if (!SUPPORTED_ENGINES.contains(normalized)) {
            log.warn("Unsupported RAG engine config ignored. key={}, value={}", CONFIG_KEY_RAG_ENGINE, engine);
            return null;
        }
        return normalized;
    }
}
