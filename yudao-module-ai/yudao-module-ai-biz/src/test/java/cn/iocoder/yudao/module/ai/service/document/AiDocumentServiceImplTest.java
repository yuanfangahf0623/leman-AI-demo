package cn.iocoder.yudao.module.ai.service.document;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentChunkDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentChunkMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentMapper;
import cn.iocoder.yudao.module.ai.enums.ChunkStatusEnum;
import cn.iocoder.yudao.module.ai.enums.DocumentEmbeddingStatusEnum;
import cn.iocoder.yudao.module.ai.enums.DocumentParseStatusEnum;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageService;
import cn.iocoder.yudao.module.ai.framework.parser.DocumentParserFactory;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVector;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVectorStore;
import cn.iocoder.yudao.module.ai.service.chunk.ChunkService;
import cn.iocoder.yudao.module.ai.service.embedding.AiEmbeddingService;
import cn.iocoder.yudao.module.ai.service.knowledge.AiKnowledgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_EMBED_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiDocumentServiceImplTest {

    @Mock
    private AiDocumentMapper documentMapper;
    @Mock
    private AiDocumentChunkMapper documentChunkMapper;
    @Mock
    private AiKnowledgeService knowledgeService;
    @Mock
    private ChunkService chunkService;
    @Mock
    private AiEmbeddingService aiEmbeddingService;
    @Mock
    private KnowledgeVectorStore knowledgeVectorStore;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private DocumentParserFactory documentParserFactory;

    private AiDocumentServiceImpl documentService;
    private AiProperties aiProperties;

    @BeforeEach
    void setUp() {
        AiTenantContextHolder.setTenantId(1L);
        aiProperties = new AiProperties();
        aiProperties.getDocument().setEmbeddingBatchSize(2);
        aiProperties.getModel().setEmbeddingModel("test-embedding-model");
        documentService = new AiDocumentServiceImpl(documentMapper, documentChunkMapper, knowledgeService,
                chunkService, aiEmbeddingService, knowledgeVectorStore, fileStorageService, documentParserFactory,
                new ObjectMapper(), aiProperties);
    }

    @AfterEach
    void tearDown() {
        AiTenantContextHolder.clear();
    }

    @Test
    void embedDocumentShouldUpdateChunksAndDocumentStatus() {
        AiDocumentDO document = buildDocument();
        AiKnowledgeBaseDO knowledgeBase = buildKnowledgeBase();
        List<AiDocumentChunkDO> chunks = List.of(
                buildChunk(1000L, 1, "first chunk"),
                buildChunk(1001L, 2, "second chunk"));
        when(documentMapper.selectByIdAndTenantId(100L, 1L)).thenReturn(document);
        when(knowledgeService.getKnowledge(10L)).thenReturn(knowledgeBase);
        when(documentChunkMapper.selectListByDocumentIdAndTenantId(100L, 10L, 1L)).thenReturn(chunks);
        when(aiEmbeddingService.embedBatch(List.of("first chunk", "second chunk")))
                .thenReturn(List.of(List.of(1.0D, 0.0D), List.of(0.0D, 1.0D)));

        documentService.embedDocument(100L);

        ArgumentCaptor<List<KnowledgeVector>> vectorCaptor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeVectorStore).upsert(vectorCaptor.capture());
        assertEquals(2, vectorCaptor.getValue().size());
        assertEquals("1:10:100:1000", vectorCaptor.getValue().get(0).getVectorId());
        assertEquals(1L, vectorCaptor.getValue().get(0).getTenantId());
        assertEquals(10L, vectorCaptor.getValue().get(0).getKnowledgeBaseId());
        verify(documentMapper).updateEmbeddingStatusByIdAndTenantId(100L, 1L,
                DocumentEmbeddingStatusEnum.RUNNING.getCode(), null);
        verify(documentChunkMapper).updateEmbeddingSuccessByIdAndTenantId(1000L, 1L,
                "1:10:100:1000", "kb-embedding-model", ChunkStatusEnum.SUCCESS.getCode());
        verify(documentChunkMapper).updateEmbeddingSuccessByIdAndTenantId(1001L, 1L,
                "1:10:100:1001", "kb-embedding-model", ChunkStatusEnum.SUCCESS.getCode());
        verify(documentMapper).updateEmbeddingStatusByIdAndTenantId(100L, 1L,
                DocumentEmbeddingStatusEnum.SUCCESS.getCode(), null);
    }

    @Test
    void embedDocumentShouldCleanupVectorAndMarkFailedWhenUpsertFailed() {
        AiDocumentDO document = buildDocument();
        AiKnowledgeBaseDO knowledgeBase = buildKnowledgeBase();
        List<AiDocumentChunkDO> chunks = List.of(buildChunk(1000L, 1, "first chunk"));
        when(documentMapper.selectByIdAndTenantId(100L, 1L)).thenReturn(document);
        when(knowledgeService.getKnowledge(10L)).thenReturn(knowledgeBase);
        when(documentChunkMapper.selectListByDocumentIdAndTenantId(100L, 10L, 1L)).thenReturn(chunks);
        when(aiEmbeddingService.embedBatch(List.of("first chunk"))).thenReturn(List.of(List.of(1.0D, 0.0D)));
        doThrow(new ServiceException(DOCUMENT_EMBED_FAILED, "向量写入失败")).when(knowledgeVectorStore).upsert(anyList());

        ServiceException exception = assertThrows(ServiceException.class, () -> documentService.embedDocument(100L));

        assertEquals(DOCUMENT_EMBED_FAILED, exception.getCode());
        verify(knowledgeVectorStore).deleteByDocumentId(100L);
        verify(documentChunkMapper).updateEmbeddingFailedByDocumentIdAndTenantId(100L, 10L, 1L,
                ChunkStatusEnum.ERROR.getCode());
        verify(documentMapper).updateEmbeddingStatusByIdAndTenantId(100L, 1L,
                DocumentEmbeddingStatusEnum.FAILED.getCode(), "向量写入失败");
    }

    private AiDocumentDO buildDocument() {
        AiDocumentDO document = new AiDocumentDO();
        document.setId(100L);
        document.setTenantId(1L);
        document.setKnowledgeBaseId(10L);
        document.setParseStatus(DocumentParseStatusEnum.SUCCESS.getCode());
        document.setEmbeddingStatus(DocumentEmbeddingStatusEnum.PENDING.getCode());
        return document;
    }

    private AiKnowledgeBaseDO buildKnowledgeBase() {
        AiKnowledgeBaseDO knowledgeBase = new AiKnowledgeBaseDO();
        knowledgeBase.setId(10L);
        knowledgeBase.setTenantId(1L);
        knowledgeBase.setEmbeddingModel("kb-embedding-model");
        return knowledgeBase;
    }

    private AiDocumentChunkDO buildChunk(Long id, Integer chunkIndex, String content) {
        AiDocumentChunkDO chunk = new AiDocumentChunkDO();
        chunk.setId(id);
        chunk.setTenantId(1L);
        chunk.setKnowledgeBaseId(10L);
        chunk.setDocumentId(100L);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setContentHash("hash-" + id);
        chunk.setTokenCount(3);
        chunk.setStatus(ChunkStatusEnum.PENDING.getCode());
        chunk.setMetadataJson("{\"chunkNo\":" + chunkIndex + "}");
        return chunk;
    }

}
