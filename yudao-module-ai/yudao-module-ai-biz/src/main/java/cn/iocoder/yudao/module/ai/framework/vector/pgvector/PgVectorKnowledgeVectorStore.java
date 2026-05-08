package cn.iocoder.yudao.module.ai.framework.vector.pgvector;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeSearchRequest;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVector;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVectorStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.module.ai.enums.AiVectorStoreErrorCodeConstants.VECTOR_STORE_CONFIG_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiVectorStoreErrorCodeConstants.VECTOR_STORE_OPERATION_FAILED;
import static cn.iocoder.yudao.module.ai.enums.AiVectorStoreErrorCodeConstants.VECTOR_STORE_REQUEST_INVALID;

/**
 * PostgreSQL + pgvector 知识库向量存储实现。
 *
 * <p>检索必须带上 tenantId 和 knowledgeBaseId，避免跨租户、跨知识库泄露数据。</p>
 */
@Slf4j
public class PgVectorKnowledgeVectorStore implements KnowledgeVectorStore {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String tableName;

    public PgVectorKnowledgeVectorStore(AiProperties aiProperties, JdbcTemplate jdbcTemplate) {
        this(aiProperties, jdbcTemplate, new ObjectMapper());
    }

    public PgVectorKnowledgeVectorStore(AiProperties aiProperties, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.tableName = buildSafeTableName(aiProperties);
    }

    @Override
    public void upsert(List<KnowledgeVector> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return;
        }
        List<KnowledgeVector> normalizedVectors = vectors.stream()
                .map(this::normalizeVector)
                .toList();
        String sql = """
                INSERT INTO %s (
                    vector_id, tenant_id, knowledge_base_id, document_id, chunk_id,
                    content, metadata, embedding, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (vector_id) DO UPDATE SET
                    tenant_id = EXCLUDED.tenant_id,
                    knowledge_base_id = EXCLUDED.knowledge_base_id,
                    document_id = EXCLUDED.document_id,
                    chunk_id = EXCLUDED.chunk_id,
                    content = EXCLUDED.content,
                    metadata = EXCLUDED.metadata,
                    embedding = EXCLUDED.embedding,
                    update_time = CURRENT_TIMESTAMP
                """.formatted(tableName);
        try {
            jdbcTemplate.batchUpdate(sql, normalizedVectors, normalizedVectors.size(), this::bindUpsert);
        } catch (DataAccessException ex) {
            log.warn("PgVector upsert failed, vectorCount={}, errorType={}, error={}",
                    normalizedVectors.size(), ex.getClass().getSimpleName(), ex.getMessage());
            throw new ServiceException(VECTOR_STORE_OPERATION_FAILED, "向量写入失败");
        }
    }

    @Override
    public List<KnowledgeHit> search(KnowledgeSearchRequest request) {
        validateSearchRequest(request);
        String queryVector = toVectorLiteral(request.getQueryEmbedding());
        String sql = """
                SELECT vector_id, tenant_id, knowledge_base_id, document_id, chunk_id,
                       content, metadata::text AS metadata_json,
                       1 - (embedding <=> ?::vector) AS score
                  FROM %s
                 WHERE tenant_id = ?
                   AND knowledge_base_id = ?
                   AND 1 - (embedding <=> ?::vector) >= ?
                 ORDER BY embedding <=> ?::vector ASC
                 LIMIT ?
                """.formatted(tableName);
        try {
            return jdbcTemplate.query(sql, ps -> bindSearch(ps, request, queryVector), (rs, rowNum) -> KnowledgeHit.builder()
                    .vectorId(rs.getString("vector_id"))
                    .tenantId(rs.getLong("tenant_id"))
                    .knowledgeBaseId(rs.getLong("knowledge_base_id"))
                    .documentId(rs.getLong("document_id"))
                    .chunkId(rs.getLong("chunk_id"))
                    .chunkNo(extractChunkNo(rs.getString("metadata_json")))
                    .content(rs.getString("content"))
                    .score(rs.getDouble("score"))
                    .metadata(parseMetadata(rs.getString("metadata_json")))
                    .build());
        } catch (DataAccessException ex) {
            log.warn("PgVector search failed, tenantId={}, knowledgeBaseId={}, topK={}, errorType={}, error={}",
                    request.getTenantId(), request.getKnowledgeBaseId(), request.getTopK(),
                    ex.getClass().getSimpleName(), ex.getMessage());
            throw new ServiceException(VECTOR_STORE_OPERATION_FAILED, "向量检索失败");
        }
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM " + tableName + " WHERE document_id = ?", documentId);
        } catch (DataAccessException ex) {
            log.warn("PgVector delete by document failed, documentId={}, errorType={}, error={}",
                    documentId, ex.getClass().getSimpleName(), ex.getMessage());
            throw new ServiceException(VECTOR_STORE_OPERATION_FAILED, "删除文档向量失败");
        }
    }

    @Override
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM " + tableName + " WHERE knowledge_base_id = ?", knowledgeBaseId);
        } catch (DataAccessException ex) {
            log.warn("PgVector delete by knowledge base failed, knowledgeBaseId={}, errorType={}, error={}",
                    knowledgeBaseId, ex.getClass().getSimpleName(), ex.getMessage());
            throw new ServiceException(VECTOR_STORE_OPERATION_FAILED, "删除知识库向量失败");
        }
    }

    private KnowledgeVector normalizeVector(KnowledgeVector vector) {
        validateVector(vector);
        String vectorId = vector.getVectorId();
        if (vectorId == null || vectorId.isBlank()) {
            vectorId = buildVectorId(vector);
        }
        if (vectorId.length() > 128) {
            throw new ServiceException(VECTOR_STORE_REQUEST_INVALID, "向量编号长度不能超过 128");
        }
        Map<String, Object> metadata = vector.getMetadata() == null ? new HashMap<>() : new HashMap<>(vector.getMetadata());
        if (vector.getChunkNo() != null) {
            metadata.put("chunkNo", vector.getChunkNo());
        }
        return KnowledgeVector.builder()
                .vectorId(vectorId)
                .tenantId(vector.getTenantId())
                .knowledgeBaseId(vector.getKnowledgeBaseId())
                .documentId(vector.getDocumentId())
                .chunkId(vector.getChunkId())
                .chunkNo(vector.getChunkNo())
                .content(vector.getContent())
                .embedding(new ArrayList<>(vector.getEmbedding()))
                .metadata(metadata)
                .build();
    }

    private void bindUpsert(PreparedStatement ps, KnowledgeVector vector) throws SQLException {
        ps.setString(1, vector.getVectorId());
        ps.setLong(2, vector.getTenantId());
        ps.setLong(3, vector.getKnowledgeBaseId());
        ps.setLong(4, vector.getDocumentId());
        ps.setLong(5, vector.getChunkId());
        ps.setString(6, vector.getContent());
        ps.setString(7, toMetadataJson(vector.getMetadata()));
        ps.setString(8, toVectorLiteral(vector.getEmbedding()));
    }

    private void bindSearch(PreparedStatement ps, KnowledgeSearchRequest request, String queryVector) throws SQLException {
        ps.setString(1, queryVector);
        ps.setLong(2, request.getTenantId());
        ps.setLong(3, request.getKnowledgeBaseId());
        ps.setString(4, queryVector);
        ps.setDouble(5, request.getScoreThreshold());
        ps.setString(6, queryVector);
        ps.setInt(7, request.getTopK());
    }

    private void validateVector(KnowledgeVector vector) {
        if (vector == null || vector.getTenantId() == null || vector.getKnowledgeBaseId() == null
                || vector.getDocumentId() == null || vector.getChunkId() == null
                || vector.getContent() == null || vector.getContent().isBlank()
                || vector.getEmbedding() == null || vector.getEmbedding().isEmpty()) {
            throw new ServiceException(VECTOR_STORE_REQUEST_INVALID, "向量写入参数不完整");
        }
        validateEmbedding(vector.getEmbedding());
    }

    private void validateSearchRequest(KnowledgeSearchRequest request) {
        if (request == null || request.getTenantId() == null || request.getKnowledgeBaseId() == null
                || request.getQueryEmbedding() == null || request.getQueryEmbedding().isEmpty()
                || request.getTopK() == null || request.getTopK() <= 0
                || request.getScoreThreshold() == null) {
            throw new ServiceException(VECTOR_STORE_REQUEST_INVALID, "向量检索参数不完整");
        }
        validateEmbedding(request.getQueryEmbedding());
    }

    private void validateEmbedding(List<Double> embedding) {
        if (embedding.stream().anyMatch(value -> value == null || value.isNaN() || value.isInfinite())) {
            throw new ServiceException(VECTOR_STORE_REQUEST_INVALID, "向量值非法");
        }
    }

    private String buildVectorId(KnowledgeVector vector) {
        return vector.getTenantId() + ":" + vector.getKnowledgeBaseId() + ":" + vector.getDocumentId() + ":" + vector.getChunkId();
    }

    private String toVectorLiteral(List<Double> embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding.get(i));
        }
        return builder.append(']').toString();
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Collections.emptyMap() : metadata);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(VECTOR_STORE_REQUEST_INVALID, "向量元数据序列化失败");
        }
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(metadataJson, METADATA_TYPE);
        } catch (JsonProcessingException ex) {
            return Collections.emptyMap();
        }
    }

    private Integer extractChunkNo(String metadataJson) {
        Object value = parseMetadata(metadataJson).get("chunkNo");
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String buildSafeTableName(AiProperties aiProperties) {
        String configuredTableName = aiProperties.getVectorStore().getPgvector().getTableName();
        if (configuredTableName == null || configuredTableName.isBlank()) {
            throw new ServiceException(VECTOR_STORE_CONFIG_INVALID, "pgvector 表名不能为空");
        }
        String[] parts = configuredTableName.trim().split("\\.");
        if (parts.length > 2) {
            throw new ServiceException(VECTOR_STORE_CONFIG_INVALID, "pgvector 表名配置非法");
        }
        List<String> safeParts = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!SQL_IDENTIFIER.matcher(part).matches()) {
                throw new ServiceException(VECTOR_STORE_CONFIG_INVALID, "pgvector 表名配置非法");
            }
            safeParts.add("\"" + part + "\"");
        }
        return String.join(".", safeParts);
    }

}
