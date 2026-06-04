package cn.iocoder.yudao.module.ai.framework.config;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.meeting.AiTeamsMeetingProperties;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVectorStore;
import cn.iocoder.yudao.module.ai.framework.vector.MockKnowledgeVectorStore;
import cn.iocoder.yudao.module.ai.framework.vector.pgvector.PgVectorKnowledgeVectorStore;
import cn.iocoder.yudao.module.ai.framework.vector.qdrant.QdrantKnowledgeVectorStore;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.chatmodel.MockChatModelService;
import cn.iocoder.yudao.module.ai.service.chatmodel.OpenAiCompatibleChatModelService;
import cn.iocoder.yudao.module.ai.service.embedding.AiEmbeddingService;
import cn.iocoder.yudao.module.ai.service.embedding.MockEmbeddingService;
import cn.iocoder.yudao.module.ai.service.embedding.OpenAiCompatibleEmbeddingService;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static cn.iocoder.yudao.module.ai.enums.AiChatModelErrorCodeConstants.CHAT_MODEL_PROVIDER_UNSUPPORTED;
import static cn.iocoder.yudao.module.ai.enums.AiEmbeddingErrorCodeConstants.EMBEDDING_PROVIDER_UNSUPPORTED;
import static cn.iocoder.yudao.module.ai.enums.AiVectorStoreErrorCodeConstants.VECTOR_STORE_CONFIG_INVALID;

/**
 * AI 模块自动配置。
 *
 * <p>当前阶段注册配置属性和 Embedding 抽象，openai-compatible 暂不创建真实外部客户端。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties({AiProperties.class, AiTeamsMeetingProperties.class})
@MapperScan("cn.iocoder.yudao.module.ai.dal.mysql")
public class AiAutoConfiguration {

    private static final String MODEL_PROVIDER_MOCK = "mock";
    private static final String MODEL_PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";
    private static final String VECTOR_STORE_MOCK = "mock";
    private static final String VECTOR_STORE_PGVECTOR = "pgvector";
    private static final String VECTOR_STORE_QDRANT = "qdrant";

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public AiEmbeddingService aiEmbeddingService(AiProperties aiProperties) {
        String provider = aiProperties.getModel().getProvider();
        // 测试环境通过 ai.model.provider=mock 使用本地固定维度向量，不触发外部模型调用。
        if (MODEL_PROVIDER_MOCK.equalsIgnoreCase(provider)) {
            return new MockEmbeddingService(aiProperties.getVectorStore().getPgvector().getDimensions());
        }
        // 第一阶段只保留 OpenAI 兼容模型结构，真实调用后续接入 Spring AI 或项目统一客户端。
        if (MODEL_PROVIDER_OPENAI_COMPATIBLE.equalsIgnoreCase(provider)) {
            return new OpenAiCompatibleEmbeddingService(aiProperties);
        }
        throw new ServiceException(EMBEDDING_PROVIDER_UNSUPPORTED, "Embedding 模型供应商不支持");
    }

    @Bean
    public AiChatModelService aiChatModelService(AiProperties aiProperties) {
        String provider = aiProperties.getModel().getProvider();
        // RAG 测试和本地开发可通过 mock 聊天模型规避外部 API 依赖。
        if (MODEL_PROVIDER_MOCK.equalsIgnoreCase(provider)) {
            return new MockChatModelService();
        }
        if (MODEL_PROVIDER_OPENAI_COMPATIBLE.equalsIgnoreCase(provider)) {
            return new OpenAiCompatibleChatModelService(aiProperties);
        }
        throw new ServiceException(CHAT_MODEL_PROVIDER_UNSUPPORTED, "Chat Model 供应商不支持");
    }

    @Bean
    public KnowledgeVectorStore knowledgeVectorStore(AiProperties aiProperties,
                                                     ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        String vectorStoreType = aiProperties.getVectorStore().getType();
        if (VECTOR_STORE_MOCK.equalsIgnoreCase(vectorStoreType)) {
            return new MockKnowledgeVectorStore();
        }
        if (VECTOR_STORE_PGVECTOR.equalsIgnoreCase(vectorStoreType)) {
            return new PgVectorKnowledgeVectorStore(aiProperties, buildPgVectorJdbcTemplate(aiProperties, jdbcTemplateProvider));
        }
        if (VECTOR_STORE_QDRANT.equalsIgnoreCase(vectorStoreType)) {
            return new QdrantKnowledgeVectorStore();
        }
        throw new ServiceException(VECTOR_STORE_CONFIG_INVALID, "向量库类型不支持");
    }

    private JdbcTemplate buildPgVectorJdbcTemplate(AiProperties aiProperties,
                                                   ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        AiProperties.PgvectorProperties pgvector = aiProperties.getVectorStore().getPgvector();
        if (hasText(pgvector.getJdbcUrl())) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setUrl(pgvector.getJdbcUrl().trim());
            dataSource.setUsername(pgvector.getUsername() == null ? "" : pgvector.getUsername().trim());
            dataSource.setPassword(pgvector.getPassword() == null ? "" : pgvector.getPassword());
            return new JdbcTemplate(dataSource);
        }
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new ServiceException(VECTOR_STORE_CONFIG_INVALID, "pgvector 需要配置 PostgreSQL JdbcTemplate");
        }
        return jdbcTemplate;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
