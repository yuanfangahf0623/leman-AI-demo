package cn.iocoder.yudao.module.ai.framework.vector;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.ai.enums.AiVectorStoreErrorCodeConstants.VECTOR_STORE_REQUEST_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockKnowledgeVectorStoreTest {

    @Test
    void searchShouldFilterTenantAndKnowledgeBase() {
        MockKnowledgeVectorStore store = new MockKnowledgeVectorStore();
        store.upsert(List.of(
                buildVector(1L, 10L, 100L, 1000L, 1, List.of(1.0D, 0.0D), "hit"),
                buildVector(2L, 10L, 101L, 1001L, 2, List.of(1.0D, 0.0D), "wrong tenant"),
                buildVector(1L, 11L, 102L, 1002L, 3, List.of(1.0D, 0.0D), "wrong kb")));

        List<KnowledgeHit> hits = store.search(KnowledgeSearchRequest.builder()
                .tenantId(1L)
                .departmentId(20L)
                .knowledgeBaseId(10L)
                .queryEmbedding(List.of(1.0D, 0.0D))
                .topK(5)
                .scoreThreshold(0.1D)
                .build());

        assertEquals(1, hits.size());
        assertEquals("hit", hits.get(0).getContent());
        assertEquals(1L, hits.get(0).getTenantId());
        assertEquals(10L, hits.get(0).getKnowledgeBaseId());
    }

    @Test
    void upsertShouldReplaceSameChunk() {
        MockKnowledgeVectorStore store = new MockKnowledgeVectorStore();
        store.upsert(List.of(buildVector(1L, 10L, 100L, 1000L, 1, List.of(1.0D, 0.0D), "old")));
        store.upsert(List.of(buildVector(1L, 10L, 100L, 1000L, 1, List.of(0.0D, 1.0D), "new")));

        List<KnowledgeHit> hits = store.search(KnowledgeSearchRequest.builder()
                .tenantId(1L)
                .departmentId(20L)
                .knowledgeBaseId(10L)
                .queryEmbedding(List.of(0.0D, 1.0D))
                .topK(5)
                .scoreThreshold(0.1D)
                .build());

        assertEquals(1, store.size());
        assertEquals(1, hits.size());
        assertEquals("new", hits.get(0).getContent());
    }

    @Test
    void deleteShouldRemoveVectors() {
        MockKnowledgeVectorStore store = new MockKnowledgeVectorStore();
        store.upsert(List.of(
                buildVector(1L, 10L, 100L, 1000L, 1, List.of(1.0D), "document 100"),
                buildVector(1L, 10L, 101L, 1001L, 2, List.of(1.0D), "document 101"),
                buildVector(1L, 11L, 102L, 1002L, 3, List.of(1.0D), "kb 11")));

        store.deleteByDocumentId(100L);
        assertEquals(2, store.size());
        List<KnowledgeHit> hits = store.search(KnowledgeSearchRequest.builder()
                .tenantId(1L)
                .departmentId(20L)
                .knowledgeBaseId(10L)
                .queryEmbedding(List.of(1.0D))
                .topK(5)
                .scoreThreshold(0.1D)
                .build());
        assertEquals(1, hits.size());
        assertEquals("document 101", hits.get(0).getContent());

        store.deleteByKnowledgeBaseId(10L);
        assertEquals(1, store.size());
    }

    @Test
    void searchShouldValidateRequiredTenantAndKnowledgeBase() {
        MockKnowledgeVectorStore store = new MockKnowledgeVectorStore();

        ServiceException exception = assertThrows(ServiceException.class, () -> store.search(KnowledgeSearchRequest.builder()
                .knowledgeBaseId(10L)
                .queryEmbedding(List.of(1.0D))
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
                .metadata(Map.of("source", "unit-test"))
                .build();
    }

}
