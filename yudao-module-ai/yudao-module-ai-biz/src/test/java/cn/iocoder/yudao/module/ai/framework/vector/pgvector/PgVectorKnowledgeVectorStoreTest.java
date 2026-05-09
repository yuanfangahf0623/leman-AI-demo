package cn.iocoder.yudao.module.ai.framework.vector.pgvector;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeSearchRequest;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.ai.enums.AiVectorStoreErrorCodeConstants.VECTOR_STORE_REQUEST_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "ai.pgvector.integration-test", matches = "true")
class PgVectorKnowledgeVectorStoreTest {

    private static final String TABLE_NAME = "ai_vector_store_it";

    private JdbcTemplate jdbcTemplate;
    private PgVectorKnowledgeVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        String jdbcUrl = System.getProperty("ai.pgvector.jdbc-url", "jdbc:postgresql://127.0.0.1:5432/yudao_ai");
        String username = System.getProperty("ai.pgvector.username", "postgres");
        String password = System.getProperty("ai.pgvector.password", "postgres");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl, username, password);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        jdbcTemplate.execute("""
                CREATE TABLE %s (
                    id BIGSERIAL PRIMARY KEY,
                    vector_id VARCHAR(128) NOT NULL UNIQUE,
                    tenant_id BIGINT NOT NULL,
                    knowledge_base_id BIGINT NOT NULL,
                    document_id BIGINT NOT NULL,
                    chunk_id BIGINT NOT NULL,
                    content TEXT NOT NULL,
                    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
                    embedding vector(3) NOT NULL,
                    create_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(TABLE_NAME));
        AiProperties properties = new AiProperties();
        properties.getVectorStore().getPgvector().setTableName(TABLE_NAME);
        properties.getVectorStore().getPgvector().setDimensions(3);
        vectorStore = new PgVectorKnowledgeVectorStore(properties, jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        }
    }

    @Test
    void upsertAndSearchShouldFilterTenantAndKnowledgeBase() {
        vectorStore.upsert(List.of(
                buildVector(1L, 10L, 100L, 1000L, 1, List.of(1.0D, 0.0D, 0.0D), "expected"),
                buildVector(2L, 10L, 101L, 1001L, 2, List.of(1.0D, 0.0D, 0.0D), "wrong tenant"),
                buildVector(1L, 11L, 102L, 1002L, 3, List.of(1.0D, 0.0D, 0.0D), "wrong kb")));

        List<KnowledgeHit> hits = vectorStore.search(KnowledgeSearchRequest.builder()
                .tenantId(1L)
                .departmentId(20L)
                .knowledgeBaseId(10L)
                .queryEmbedding(List.of(1.0D, 0.0D, 0.0D))
                .topK(5)
                .scoreThreshold(0.9D)
                .build());

        assertEquals(1, hits.size());
        assertEquals("expected", hits.get(0).getContent());
        assertEquals(1L, hits.get(0).getTenantId());
        assertEquals(10L, hits.get(0).getKnowledgeBaseId());
        assertEquals(1, hits.get(0).getChunkNo());
    }

    @Test
    void searchShouldApplyTopKAndScoreThreshold() {
        vectorStore.upsert(List.of(
                buildVector(1L, 10L, 100L, 1000L, 1, List.of(1.0D, 0.0D, 0.0D), "best"),
                buildVector(1L, 10L, 100L, 1001L, 2, List.of(0.8D, 0.2D, 0.0D), "second"),
                buildVector(1L, 10L, 100L, 1002L, 3, List.of(0.0D, 1.0D, 0.0D), "low score")));

        List<KnowledgeHit> hits = vectorStore.search(KnowledgeSearchRequest.builder()
                .tenantId(1L)
                .departmentId(20L)
                .knowledgeBaseId(10L)
                .queryEmbedding(List.of(1.0D, 0.0D, 0.0D))
                .topK(1)
                .scoreThreshold(0.5D)
                .build());

        assertEquals(1, hits.size());
        assertEquals("best", hits.get(0).getContent());
        assertEquals(1.0D, hits.get(0).getScore(), 0.000001D);
    }

    @Test
    void deleteByDocumentIdShouldRemoveVectors() {
        vectorStore.upsert(List.of(
                buildVector(1L, 10L, 100L, 1000L, 1, List.of(1.0D, 0.0D, 0.0D), "deleted"),
                buildVector(1L, 10L, 101L, 1001L, 2, List.of(1.0D, 0.0D, 0.0D), "remain")));

        vectorStore.deleteByDocumentId(100L);

        List<KnowledgeHit> hits = vectorStore.search(KnowledgeSearchRequest.builder()
                .tenantId(1L)
                .departmentId(20L)
                .knowledgeBaseId(10L)
                .queryEmbedding(List.of(1.0D, 0.0D, 0.0D))
                .topK(5)
                .scoreThreshold(0.1D)
                .build());
        assertEquals(1, hits.size());
        assertEquals("remain", hits.get(0).getContent());
    }

    @Test
    void deleteByKnowledgeBaseIdShouldRemoveVectors() {
        vectorStore.upsert(List.of(
                buildVector(1L, 10L, 100L, 1000L, 1, List.of(1.0D, 0.0D, 0.0D), "deleted"),
                buildVector(1L, 11L, 101L, 1001L, 2, List.of(1.0D, 0.0D, 0.0D), "remain")));

        vectorStore.deleteByKnowledgeBaseId(10L);

        List<KnowledgeHit> hits = vectorStore.search(KnowledgeSearchRequest.builder()
                .tenantId(1L)
                .departmentId(20L)
                .knowledgeBaseId(11L)
                .queryEmbedding(List.of(1.0D, 0.0D, 0.0D))
                .topK(5)
                .scoreThreshold(0.1D)
                .build());
        assertEquals(1, hits.size());
        assertEquals("remain", hits.get(0).getContent());
    }

    @Test
    void searchShouldRejectMissingTenantId() {
        ServiceException exception = assertThrows(ServiceException.class, () -> vectorStore.search(KnowledgeSearchRequest.builder()
                .knowledgeBaseId(10L)
                .queryEmbedding(List.of(1.0D, 0.0D, 0.0D))
                .topK(5)
                .scoreThreshold(0.1D)
                .build()));

        assertEquals(VECTOR_STORE_REQUEST_INVALID, exception.getCode());
    }

    private KnowledgeVector buildVector(Long tenantId, Long knowledgeBaseId, Long documentId, Long chunkId,
                                        Integer chunkNo, List<Double> embedding, String content) {
        return KnowledgeVector.builder()
                .tenantId(tenantId)
                .knowledgeBaseId(knowledgeBaseId)
                .documentId(documentId)
                .chunkId(chunkId)
                .chunkNo(chunkNo)
                .content(content)
                .embedding(embedding)
                .metadata(Map.of("source", "pgvector-it"))
                .build();
    }

}
