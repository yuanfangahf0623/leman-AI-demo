package cn.iocoder.yudao.module.ai.service.rag;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatMessageDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatQuestionCacheDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatCitationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatConversationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatMessageMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatQuestionCacheMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentChunkMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiKnowledgeBaseMapper;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeSearchRequest;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVectorStore;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelRequest;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.embedding.AiEmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_KNOWLEDGE_ACCESS_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    @Mock
    private AiKnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private AiChatConversationMapper chatConversationMapper;
    @Mock
    private AiChatMessageMapper chatMessageMapper;
    @Mock
    private AiChatCitationMapper chatCitationMapper;
    @Mock
    private AiChatQuestionCacheMapper chatQuestionCacheMapper;
    @Mock
    private AiDocumentChunkMapper documentChunkMapper;
    @Mock
    private AiEmbeddingService aiEmbeddingService;
    @Mock
    private KnowledgeVectorStore knowledgeVectorStore;
    @Mock
    private AiChatModelService aiChatModelService;

    private RagServiceImpl ragService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AiUserContextHolder.setUserContext(1L, 100L, 20L);
        AiProperties aiProperties = new AiProperties();
        aiProperties.getRag().setDefaultScoreThreshold(0.6D);
        objectMapper = new ObjectMapper();
        ragService = new RagServiceImpl(knowledgeBaseMapper, chatConversationMapper, chatMessageMapper,
                chatCitationMapper, chatQuestionCacheMapper, documentChunkMapper, aiEmbeddingService,
                knowledgeVectorStore, new PromptBuilder(aiProperties), aiChatModelService, objectMapper,
                aiProperties);
    }

    @AfterEach
    void tearDown() {
        AiUserContextHolder.clear();
    }

    @Test
    void chatShouldSearchBuildPromptCallModelAndSaveLogs() {
        mockConversationAndMessageIds();
        mockCitationId();
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("20,30"));
        when(aiEmbeddingService.embed("报销流程是什么？")).thenReturn(List.of(1.0D, 0.0D));
        when(knowledgeVectorStore.search(any(KnowledgeSearchRequest.class))).thenReturn(List.of(KnowledgeHit.builder()
                .tenantId(1L)
                .knowledgeBaseId(10L)
                .documentId(200L)
                .chunkId(300L)
                .chunkNo(2)
                .documentTitle("财务制度")
                .content("员工报销需要提交发票和审批单。")
                .score(0.91D)
                .metadata(Map.of("title", "财务制度"))
                .build()));
        when(aiChatModelService.chat(any(AiChatModelRequest.class))).thenReturn(AiChatModelResponse.builder()
                .model("unit-test-chat-model")
                .content("报销需要提交发票和审批单。")
                .promptTokens(10)
                .completionTokens(5)
                .totalTokens(15)
                .build());

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question("报销流程是什么？")
                .build());

        assertFalse(response.getNoContext());
        assertEquals("报销需要提交发票和审批单。", response.getAnswer());
        assertEquals(1, response.getCitations().size());
        assertEquals("财务制度", response.getCitations().get(0).getDocumentTitle());

        ArgumentCaptor<KnowledgeSearchRequest> searchCaptor = ArgumentCaptor.forClass(KnowledgeSearchRequest.class);
        verify(knowledgeVectorStore).search(searchCaptor.capture());
        assertEquals(1L, searchCaptor.getValue().getTenantId());
        assertEquals(20L, searchCaptor.getValue().getDepartmentId());
        assertEquals(10L, searchCaptor.getValue().getKnowledgeBaseId());
        assertEquals(3, searchCaptor.getValue().getTopK());
        assertEquals(0.6D, searchCaptor.getValue().getScoreThreshold());

        ArgumentCaptor<AiChatModelRequest> chatCaptor = ArgumentCaptor.forClass(AiChatModelRequest.class);
        verify(aiChatModelService).chat(chatCaptor.capture());
        assertTrue(chatCaptor.getValue().getSystemPrompt().contains("严格基于给定的知识片段"));
        assertTrue(chatCaptor.getValue().getUserPrompt().contains("财务制度"));

        ArgumentCaptor<AiChatCitationDO> citationCaptor = ArgumentCaptor.forClass(AiChatCitationDO.class);
        verify(chatCitationMapper).insert(citationCaptor.capture());
        assertEquals(1L, citationCaptor.getValue().getTenantId());
        assertEquals(20L, citationCaptor.getValue().getDepartmentId());
        assertEquals(10L, citationCaptor.getValue().getKnowledgeBaseId());
        assertEquals(200L, citationCaptor.getValue().getDocumentId());
        assertEquals(300L, citationCaptor.getValue().getChunkId());
        assertEquals("财务制度", citationCaptor.getValue().getDocumentTitle());
        assertEquals("员工报销需要提交发票和审批单。", citationCaptor.getValue().getContentSnapshot());

        ArgumentCaptor<AiChatMessageDO> messageCaptor = ArgumentCaptor.forClass(AiChatMessageDO.class);
        verify(chatMessageMapper, times(2)).insert(messageCaptor.capture());
        AiChatMessageDO assistantMessage = messageCaptor.getAllValues().get(1);
        assertEquals("unit-test-chat-model", assistantMessage.getModel());
        assertEquals(15, assistantMessage.getTotalTokens());
        assertTrue(assistantMessage.getLatencyMs() >= 0L);
    }

    @Test
    void chatShouldUseQuestionCacheAndSkipEmbeddingVectorAndModel() throws Exception {
        mockConversationAndMessageIds();
        mockCitationId();
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        String citationSnapshotJson = objectMapper.writeValueAsString(List.of(RagChatCitation.builder()
                .documentId(200L)
                .chunkId(300L)
                .chunkNo(2)
                .documentTitle("财务制度")
                .score(0.91D)
                .quoteText("员工报销需要提交发票和审批单。")
                .build()));
        when(chatQuestionCacheMapper.selectLatest(eq(1L), eq(20L), eq(10L), anyString()))
                .thenReturn(AiChatQuestionCacheDO.builder()
                        .id(900L)
                        .tenantId(1L)
                        .departmentId(20L)
                        .knowledgeBaseId(10L)
                        .answer("报销需要提交发票和审批单。")
                        .model("unit-test-chat-model")
                        .promptTokens(10)
                        .completionTokens(5)
                        .totalTokens(15)
                        .citationSnapshotJson(citationSnapshotJson)
                        .build());

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question("报销流程是什么？")
                .build());

        assertFalse(response.getNoContext());
        assertEquals("报销需要提交发票和审批单。", response.getAnswer());
        assertEquals(1, response.getCitations().size());
        verify(chatQuestionCacheMapper).updateHitCount(900L, 1L, 20L);
        verifyNoInteractions(aiEmbeddingService, knowledgeVectorStore, aiChatModelService);

        ArgumentCaptor<AiChatMessageDO> messageCaptor = ArgumentCaptor.forClass(AiChatMessageDO.class);
        verify(chatMessageMapper, times(2)).insert(messageCaptor.capture());
        AiChatMessageDO assistantMessage = messageCaptor.getAllValues().get(1);
        assertEquals("unit-test-chat-model", assistantMessage.getModel());
        assertEquals(15, assistantMessage.getTotalTokens());
        assertEquals(0L, assistantMessage.getLatencyMs());
    }

    @Test
    void chatShouldReturnFallbackAndSkipModelWhenNoHit() {
        mockConversationAndMessageIds();
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        when(aiEmbeddingService.embed("未知问题")).thenReturn(List.of(1.0D, 0.0D));
        when(knowledgeVectorStore.search(any(KnowledgeSearchRequest.class))).thenReturn(List.of());

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question("未知问题")
                .build());

        assertTrue(response.getNoContext());
        assertEquals("根据当前知识库资料无法确认", response.getAnswer());
        assertTrue(response.getCitations().isEmpty());
        verify(aiChatModelService, never()).chat(any());
        verify(chatCitationMapper, never()).insert(any(AiChatCitationDO.class));
    }

    @Test
    void chatShouldRejectDepartmentWithoutPermission() {
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("99"));

        ServiceException exception = assertThrows(ServiceException.class, () -> ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question("报销流程是什么？")
                .build()));

        assertEquals(RAG_KNOWLEDGE_ACCESS_DENIED, exception.getCode());
        verifyNoInteractions(aiEmbeddingService, knowledgeVectorStore, aiChatModelService);
    }

    private void mockConversationAndMessageIds() {
        when(chatConversationMapper.insert(any(AiChatConversationDO.class))).thenAnswer(invocation -> {
            AiChatConversationDO conversation = invocation.getArgument(0);
            conversation.setId(500L);
            return 1;
        });
        AtomicLong messageId = new AtomicLong(1000L);
        when(chatMessageMapper.insert(any(AiChatMessageDO.class))).thenAnswer(invocation -> {
            AiChatMessageDO message = invocation.getArgument(0);
            message.setId(messageId.getAndIncrement());
            return 1;
        });
        when(chatConversationMapper.updateLastMessageTime(eq(500L), eq(1L), eq(20L), any())).thenReturn(1);
    }

    private void mockCitationId() {
        when(chatCitationMapper.insert(any(AiChatCitationDO.class))).thenAnswer(invocation -> {
            AiChatCitationDO citation = invocation.getArgument(0);
            citation.setId(2000L);
            return 1;
        });
    }

    private AiKnowledgeBaseDO buildKnowledge(String departmentIds) {
        return AiKnowledgeBaseDO.builder()
                .id(10L)
                .tenantId(1L)
                .departmentIds(departmentIds)
                .name("财务知识库")
                .topK(3)
                .build();
    }

}
