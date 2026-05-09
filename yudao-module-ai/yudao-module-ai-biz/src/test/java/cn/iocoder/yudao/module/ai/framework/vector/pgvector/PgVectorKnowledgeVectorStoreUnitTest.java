package cn.iocoder.yudao.module.ai.framework.vector.pgvector;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVector;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static cn.iocoder.yudao.module.ai.enums.AiVectorStoreErrorCodeConstants.VECTOR_STORE_REQUEST_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PgVectorKnowledgeVectorStoreUnitTest {

    @Test
    void upsertShouldRejectDimensionMismatchBeforeJdbcCall() {
        AiProperties properties = new AiProperties();
        properties.getVectorStore().getPgvector().setDimensions(3);
        PgVectorKnowledgeVectorStore vectorStore = new PgVectorKnowledgeVectorStore(properties, mock(JdbcTemplate.class));

        ServiceException exception = assertThrows(ServiceException.class, () -> vectorStore.upsert(List.of(
                KnowledgeVector.builder()
                        .tenantId(1L)
                        .knowledgeBaseId(10L)
                        .documentId(100L)
                        .chunkId(1000L)
                        .content("dimension mismatch")
                        .embedding(List.of(1.0D, 0.0D))
                        .build())));

        assertEquals(VECTOR_STORE_REQUEST_INVALID, exception.getCode());
    }

}
