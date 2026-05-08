package cn.iocoder.yudao.module.ai.framework.vector;

import cn.iocoder.yudao.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.yudao.module.ai.enums.AiVectorStoreErrorCodeConstants.VECTOR_STORE_REQUEST_INVALID;

/**
 * 测试环境使用的内存向量库实现。
 *
 * <p>该实现只用于联调和单元测试，不依赖 pgvector 或 Qdrant SDK。</p>
 */
public class MockKnowledgeVectorStore implements KnowledgeVectorStore {

    private final Map<String, KnowledgeVector> vectors = new ConcurrentHashMap<>();

    @Override
    public void upsert(List<KnowledgeVector> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return;
        }
        for (KnowledgeVector vector : vectors) {
            validateVector(vector);
            this.vectors.put(buildVectorKey(vector), copyVector(vector));
        }
    }

    @Override
    public List<KnowledgeHit> search(KnowledgeSearchRequest request) {
        validateSearchRequest(request);
        return vectors.values().stream()
                .filter(vector -> Objects.equals(vector.getTenantId(), request.getTenantId()))
                .filter(vector -> Objects.equals(vector.getKnowledgeBaseId(), request.getKnowledgeBaseId()))
                .map(vector -> toHit(vector, cosineSimilarity(request.getQueryEmbedding(), vector.getEmbedding())))
                .filter(hit -> hit.getScore() >= request.getScoreThreshold())
                .sorted(Comparator.comparing(KnowledgeHit::getScore).reversed()
                        .thenComparing(hit -> hit.getChunkNo() == null ? Integer.MAX_VALUE : hit.getChunkNo()))
                .limit(request.getTopK())
                .toList();
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        vectors.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getDocumentId(), documentId));
    }

    @Override
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return;
        }
        vectors.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getKnowledgeBaseId(), knowledgeBaseId));
    }

    public int size() {
        return vectors.size();
    }

    private void validateVector(KnowledgeVector vector) {
        if (vector == null || vector.getTenantId() == null || vector.getKnowledgeBaseId() == null
                || vector.getDocumentId() == null || vector.getChunkId() == null
                || vector.getEmbedding() == null || vector.getEmbedding().isEmpty()) {
            throw new ServiceException(VECTOR_STORE_REQUEST_INVALID, "向量写入参数不完整");
        }
    }

    private void validateSearchRequest(KnowledgeSearchRequest request) {
        if (request == null || request.getTenantId() == null || request.getKnowledgeBaseId() == null
                || request.getQueryEmbedding() == null || request.getQueryEmbedding().isEmpty()
                || request.getTopK() == null || request.getTopK() <= 0
                || request.getScoreThreshold() == null) {
            throw new ServiceException(VECTOR_STORE_REQUEST_INVALID, "向量检索参数不完整");
        }
    }

    private String buildVectorKey(KnowledgeVector vector) {
        if (vector.getVectorId() != null && !vector.getVectorId().isBlank()) {
            return vector.getVectorId();
        }
        return vector.getTenantId() + ":" + vector.getKnowledgeBaseId() + ":" + vector.getDocumentId() + ":" + vector.getChunkId();
    }

    private KnowledgeVector copyVector(KnowledgeVector vector) {
        return KnowledgeVector.builder()
                .vectorId(buildVectorKey(vector))
                .tenantId(vector.getTenantId())
                .knowledgeBaseId(vector.getKnowledgeBaseId())
                .documentId(vector.getDocumentId())
                .chunkId(vector.getChunkId())
                .chunkNo(vector.getChunkNo())
                .content(vector.getContent())
                .embedding(new ArrayList<>(vector.getEmbedding()))
                .metadata(vector.getMetadata() == null ? Collections.emptyMap() : new HashMap<>(vector.getMetadata()))
                .build();
    }

    private KnowledgeHit toHit(KnowledgeVector vector, double score) {
        return KnowledgeHit.builder()
                .vectorId(vector.getVectorId())
                .tenantId(vector.getTenantId())
                .knowledgeBaseId(vector.getKnowledgeBaseId())
                .documentId(vector.getDocumentId())
                .chunkId(vector.getChunkId())
                .chunkNo(vector.getChunkNo())
                .content(vector.getContent())
                .score(score)
                .metadata(vector.getMetadata())
                .build();
    }

    private double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left.size() != right.size()) {
            throw new ServiceException(VECTOR_STORE_REQUEST_INVALID, "向量维度不一致");
        }
        double dot = 0.0D;
        double leftNorm = 0.0D;
        double rightNorm = 0.0D;
        for (int i = 0; i < left.size(); i++) {
            double leftValue = left.get(i) == null ? 0.0D : left.get(i);
            double rightValue = right.get(i) == null ? 0.0D : right.get(i);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0.0D || rightNorm == 0.0D) {
            return 0.0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

}
