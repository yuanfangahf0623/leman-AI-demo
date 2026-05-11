package cn.iocoder.yudao.module.ai.service.chunk;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentChunkMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentMapper;
import cn.iocoder.yudao.module.ai.framework.parser.ParsedDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_CHUNK_CREATE_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChunkServiceImplTest {

    @Mock
    private AiDocumentChunkMapper documentChunkMapper;
    @Mock
    private AiDocumentMapper documentMapper;

    @Test
    void recreateChunksShouldFailWhenParsedContentEmpty() {
        ChunkServiceImpl chunkService = new ChunkServiceImpl(documentChunkMapper, documentMapper,
                new DocumentChunkSplitter(), new ObjectMapper());

        ServiceException exception = assertThrows(ServiceException.class, () -> chunkService.recreateChunks(
                AiKnowledgeBaseDO.builder().id(10L).chunkSize(800).chunkOverlap(100).build(),
                AiDocumentDO.builder().id(14L).tenantId(1L).knowledgeBaseId(10L).build(),
                new ParsedDocument("扫描件", "   ", Map.of())));

        assertEquals(DOCUMENT_CHUNK_CREATE_FAILED, exception.getCode());
        verifyNoInteractions(documentChunkMapper, documentMapper);
    }

}
