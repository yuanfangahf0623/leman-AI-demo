package cn.iocoder.yudao.module.ai.service.rag;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatMessageDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatQuestionCacheDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentChunkDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatCitationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatConversationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatMessageMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatQuestionCacheMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentChunkMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiKnowledgeBaseMapper;
import cn.iocoder.yudao.module.ai.enums.ChatMessageRoleEnum;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeSearchRequest;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVectorStore;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelRequest;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelMessage;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.embedding.AiEmbeddingService;
import cn.iocoder.yudao.module.ai.service.rag.config.AiRagEngineConfigService;
import cn.iocoder.yudao.module.ai.service.rag.fastgpt.FastGptRagClient;
import cn.iocoder.yudao.module.ai.service.rag.fastgpt.FastGptRagRequest;
import cn.iocoder.yudao.module.ai.service.rag.fastgpt.FastGptRagResult;
import cn.iocoder.yudao.module.ai.service.rag.retrieval.RetrievalPlanner;
import cn.iocoder.yudao.module.ai.service.rag.sensitive.PersonalSensitiveDataPolicy;
import cn.iocoder.yudao.module.ai.service.websearch.WebSearchResult;
import cn.iocoder.yudao.module.ai.service.websearch.WebSearchService;
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
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_PERSONAL_SENSITIVE_ACCESS_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
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
    private WebSearchService webSearchService;
    @Mock
    private AiChatModelService aiChatModelService;
    @Mock
    private FastGptRagClient fastGptRagClient;
    @Mock
    private AiRagEngineConfigService ragEngineConfigService;

    private RagServiceImpl ragService;
    private ObjectMapper objectMapper;
    private AiProperties aiProperties;

    @BeforeEach
    void setUp() {
        AiUserContextHolder.setUserContext(1L, 100L, 20L);
        aiProperties = new AiProperties();
        aiProperties.getRag().setDefaultScoreThreshold(0.6D);
        objectMapper = new ObjectMapper();
        ragService = new RagServiceImpl(knowledgeBaseMapper, chatConversationMapper, chatMessageMapper,
                chatCitationMapper, chatQuestionCacheMapper, documentChunkMapper, aiEmbeddingService,
                knowledgeVectorStore, webSearchService, new PromptBuilder(aiProperties), new RetrievalPlanner(),
                aiChatModelService, fastGptRagClient, ragEngineConfigService, objectMapper, aiProperties,
                new PersonalSensitiveDataPolicy(null));
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
        assertTrue(response.getDebugInfo().contains("RAG 平台层执行轨迹"));
        assertTrue(response.getDebugInfo().contains("ragEngine=local"));
        assertTrue(response.getDebugInfo().contains("contextHitSummary"));

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
    void chatShouldUseFastGptOnlyWhenEngineEnabledAndLocalHasNoContext() {
        aiProperties.getRag().setEngine("fastgpt");
        when(ragEngineConfigService.isFastGptEngine()).thenReturn(true);
        mockConversationAndMessageIds();
        mockCitationId();
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        when(fastGptRagClient.chat(any(FastGptRagRequest.class))).thenReturn(FastGptRagResult.builder()
                .modelResponse(AiChatModelResponse.builder()
                        .model("fastgpt")
                        .content("FastGPT answer")
                        .promptTokens(11)
                        .completionTokens(7)
                        .totalTokens(18)
                        .build())
                .citations(List.of(RagChatCitation.builder()
                        .documentTitle("FastGPT source")
                        .quoteText("FastGPT quote")
                        .score(0.88D)
                        .build()))
                .build());

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question("What does FastGPT answer?")
                .build());

        assertEquals("FastGPT answer", response.getAnswer());
        assertFalse(response.getNoContext());
        assertEquals(1, response.getCitations().size());
        assertEquals(10L, response.getCitations().get(0).getKnowledgeBaseId());
        assertTrue(response.getDebugInfo().contains("RAG 平台层执行轨迹"));
        assertTrue(response.getDebugInfo().contains("ragEngine=fastgpt+local"));
        assertTrue(response.getDebugInfo().contains("finalAnswerSource=fastgpt-only"));
        ArgumentCaptor<FastGptRagRequest> requestCaptor = ArgumentCaptor.forClass(FastGptRagRequest.class);
        verify(fastGptRagClient).chat(requestCaptor.capture());
        FastGptRagRequest fastGptRequest = requestCaptor.getValue();
        assertEquals(1L, fastGptRequest.getTenantId());
        assertEquals(20L, fastGptRequest.getDepartmentId());
        assertEquals(10L, fastGptRequest.getKnowledgeBaseId());
        assertEquals(500L, fastGptRequest.getConversationId());
        assertEquals(100L, fastGptRequest.getUserId());
        assertEquals("What does FastGPT answer?", fastGptRequest.getQuestion());
        assertEquals(ChatMessageRoleEnum.USER.getCode(), fastGptRequest.getMessages().get(0).getRole());
        assertEquals("What does FastGPT answer?", fastGptRequest.getMessages().get(0).getContent());
        verify(aiEmbeddingService).embed("What does FastGPT answer?");
        verify(knowledgeVectorStore).search(any(KnowledgeSearchRequest.class));
        verify(aiChatModelService, never()).chat(any());
    }

    @Test
    void chatShouldMergeFastGptAndLocalKnowledgeWhenEngineEnabled() {
        aiProperties.getRag().setEngine("fastgpt");
        when(ragEngineConfigService.isFastGptEngine()).thenReturn(true);
        mockConversationAndMessageIds();
        mockCitationId();
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        when(fastGptRagClient.chat(any(FastGptRagRequest.class))).thenReturn(FastGptRagResult.builder()
                .modelResponse(AiChatModelResponse.builder()
                        .model("fastgpt")
                        .content("FastGPT general answer")
                        .build())
                .citations(List.of(RagChatCitation.builder()
                        .knowledgeBaseName("FastGPT")
                        .documentTitle("FastGPT source")
                        .quoteText("FastGPT quote")
                        .score(0.80D)
                        .build()))
                .build());
        when(aiEmbeddingService.embed("What is attendance?")).thenReturn(List.of(1.0D, 0.0D));
        when(knowledgeVectorStore.search(any(KnowledgeSearchRequest.class))).thenReturn(List.of(KnowledgeHit.builder()
                .tenantId(1L)
                .knowledgeBaseId(10L)
                .documentId(210L)
                .chunkId(310L)
                .chunkNo(1)
                .documentTitle("2haohr attendance")
                .content("Local HR attendance rule")
                .score(0.92D)
                .metadata(Map.of("title", "2haohr attendance"))
                .build()));
        when(aiChatModelService.chat(any(AiChatModelRequest.class))).thenReturn(AiChatModelResponse.builder()
                .model("unit-test-chat-model")
                .content("Hybrid answer")
                .totalTokens(12)
                .build());

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question("What is attendance?")
                .build());

        assertEquals("Hybrid answer", response.getAnswer());
        assertFalse(response.getNoContext());
        assertEquals(2, response.getCitations().size());
        assertEquals("2haohr attendance", response.getCitations().get(0).getDocumentTitle());
        assertEquals("FastGPT source", response.getCitations().get(1).getDocumentTitle());
        assertTrue(response.getDebugInfo().contains("ragEngine=fastgpt+local"));
        assertTrue(response.getDebugInfo().contains("finalAnswerSource=local-model-hybrid"));

        ArgumentCaptor<AiChatModelRequest> chatCaptor = ArgumentCaptor.forClass(AiChatModelRequest.class);
        verify(aiChatModelService).chat(chatCaptor.capture());
        assertTrue(chatCaptor.getValue().getUserPrompt().contains("FastGPT general answer"));
        assertTrue(chatCaptor.getValue().getUserPrompt().contains("Local HR attendance rule"));

        ArgumentCaptor<KnowledgeSearchRequest> searchCaptor = ArgumentCaptor.forClass(KnowledgeSearchRequest.class);
        verify(knowledgeVectorStore).search(searchCaptor.capture());
        assertEquals(1L, searchCaptor.getValue().getTenantId());
        assertEquals(20L, searchCaptor.getValue().getDepartmentId());
        assertEquals(10L, searchCaptor.getValue().getKnowledgeBaseId());
    }

    @Test
    void chatShouldNotMapFastGptExternalDatasetIdToLocalKnowledgeBaseWhenAllKnowledgeSelected() {
        aiProperties.getRag().setEngine("fastgpt");
        when(ragEngineConfigService.isFastGptEngine()).thenReturn(true);
        mockConversationAndMessageIds();
        mockCitationId();
        when(knowledgeBaseMapper.selectListByTenantId(1L)).thenReturn(List.of(buildKnowledge("*")));
        when(fastGptRagClient.chat(any(FastGptRagRequest.class))).thenReturn(FastGptRagResult.builder()
                .modelResponse(AiChatModelResponse.builder()
                        .model("fastgpt")
                        .content("FastGPT answer")
                        .build())
                .citations(List.of(RagChatCitation.builder()
                        .knowledgeBaseId(12L)
                        .documentTitle("开票资料.docx")
                        .quoteText("开票信息")
                        .build()))
                .build());

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(0L)
                .question("开票信息")
                .build());

        assertEquals(0L, response.getCitations().get(0).getKnowledgeBaseId());
        ArgumentCaptor<AiChatCitationDO> citationCaptor = ArgumentCaptor.forClass(AiChatCitationDO.class);
        verify(chatCitationMapper).insert(citationCaptor.capture());
        assertEquals(0L, citationCaptor.getValue().getKnowledgeBaseId());
    }

    @Test
    void chatShouldAnswerCurrentLoginNicknameWhenAskedWhoAmI() {
        AiUserContextHolder.setUserContext(1L, 100L, 20L, "管理员", false);
        mockConversationAndMessageIds();
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question("我是谁？")
                .build());

        assertFalse(response.getNoContext());
        assertEquals("你是管理员。", response.getAnswer());
        assertTrue(response.getCitations().isEmpty());
        verifyNoInteractions(chatQuestionCacheMapper, aiEmbeddingService, knowledgeVectorStore, documentChunkMapper,
                aiChatModelService);
    }

    @Test
    void chatShouldMergeLexicalSupplementWhenVectorMissesRelatedPolicy() {
        AiUserContextHolder.setUserContext(1L, 100L, 20L, "温春雨", false);
        mockConversationAndMessageIds();
        mockCitationId();
        String question = "如果我是车间主任，我全勤的且满绩效的情况下可以拿到多少钱？";
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        when(aiEmbeddingService.embed(question)).thenReturn(List.of(1.0D, 0.0D));
        when(knowledgeVectorStore.search(any(KnowledgeSearchRequest.class))).thenReturn(List.of(KnowledgeHit.builder()
                .tenantId(1L)
                .knowledgeBaseId(10L)
                .documentId(201L)
                .chunkId(301L)
                .chunkNo(1)
                .documentTitle("绩效考核制度")
                .content("车间主任满绩效奖金为 1000 元。")
                .score(0.88D)
                .metadata(Map.of("title", "绩效考核制度"))
                .build()));
        when(documentChunkMapper.selectLexicalCandidates(eq(1L), eq(10L), anyList(), anyInt()))
                .thenReturn(List.of(
                        buildChunk(302L, 201L, 2, "绩效考核制度", "车间主任绩效工资按绩效结果计算，满绩效时发放 1000 元。"),
                        buildChunk(303L, 201L, 3, "绩效考核制度", "车间主任现场5S绩效、质量绩效、产量绩效均达标时不扣绩效工资。"),
                        buildChunk(304L, 201L, 4, "绩效考核制度", "车间主任绩效奖金和岗位工资按月度绩效考核结果核算。"),
                        buildChunk(305L, 201L, 5, "绩效考核制度", "满绩效情况下，绩效工资按制度表格中的标准金额发放。"),
                        buildChunk(306L, 201L, 6, "绩效考核制度", "车间主任绩效达标且质量无异常时，绩效部分不扣款。"),
                        buildChunk(307L, 202L, 1, "考勤制度", "员工当月全勤时，全勤奖为 50 元。")
                ));
        when(aiChatModelService.chat(any(AiChatModelRequest.class))).thenReturn(AiChatModelResponse.builder()
                .model("unit-test-chat-model")
                .content("车间主任满绩效奖金 1000 元，全勤奖 50 元，合计 1050 元。")
                .promptTokens(20)
                .completionTokens(10)
                .totalTokens(30)
                .build());

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question(question)
                .build());

        assertFalse(response.getNoContext());
        assertTrue(response.getCitations().stream()
                .anyMatch(citation -> "绩效考核制度".equals(citation.getDocumentTitle())));
        assertTrue(response.getCitations().stream()
                .anyMatch(citation -> "考勤制度".equals(citation.getDocumentTitle())));

        ArgumentCaptor<AiChatModelRequest> chatCaptor = ArgumentCaptor.forClass(AiChatModelRequest.class);
        verify(aiChatModelService).chat(chatCaptor.capture());
        assertTrue(chatCaptor.getValue().getUserPrompt().contains("车间主任满绩效奖金"));
        assertTrue(chatCaptor.getValue().getUserPrompt().contains("全勤奖为 50 元"));
    }

    @Test
    void chatShouldRejectPersonalSensitiveQuestionWhenNonAdminQueriesOtherPerson() {
        AiUserContextHolder.setUserContext(1L, 100L, 20L, "温春雨", false);
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));

        ServiceException exception = assertThrows(ServiceException.class, () -> ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question("张增波工资是多少？")
                .build()));

        assertEquals(RAG_PERSONAL_SENSITIVE_ACCESS_DENIED, exception.getCode());
        verifyNoInteractions(chatConversationMapper, chatMessageMapper, chatQuestionCacheMapper, aiEmbeddingService,
                knowledgeVectorStore, documentChunkMapper, aiChatModelService);
    }

    @Test
    void chatShouldRejectIdCardQuestionWhenNonAdminQueriesOtherPerson() {
        AiUserContextHolder.setUserContext(1L, 100L, 20L, "温春雨", false);
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));

        ServiceException exception = assertThrows(ServiceException.class, () -> ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question("张增波身份证号是多少？")
                .build()));

        assertEquals(RAG_PERSONAL_SENSITIVE_ACCESS_DENIED, exception.getCode());
        verifyNoInteractions(chatConversationMapper, chatMessageMapper, chatQuestionCacheMapper, aiEmbeddingService,
                knowledgeVectorStore, documentChunkMapper, aiChatModelService);
    }

    @Test
    void chatShouldFilterOtherPersonalSensitiveHitsForNonAdminSelfQuestion() {
        AiUserContextHolder.setUserContext(1L, 100L, 20L, "温春雨", false);
        mockConversationAndMessageIds();
        mockCitationId();
        String question = "我全勤且满绩效可以拿到多少钱？";
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        when(aiEmbeddingService.embed(question)).thenReturn(List.of(1.0D, 0.0D));
        when(knowledgeVectorStore.search(any(KnowledgeSearchRequest.class))).thenReturn(List.of(
                KnowledgeHit.builder()
                        .tenantId(1L)
                        .knowledgeBaseId(10L)
                        .documentId(15L)
                        .chunkId(66L)
                        .chunkNo(4)
                        .documentTitle("绩效考核制度")
                        .content("Sheet: 温春雨\n绩效目标确认单\n薪资结构：车间主任综合薪资 16000元/月。")
                        .score(0.90D)
                        .metadata(Map.of("title", "绩效考核制度"))
                        .build(),
                KnowledgeHit.builder()
                        .tenantId(1L)
                        .knowledgeBaseId(10L)
                        .documentId(15L)
                        .chunkId(72L)
                        .chunkNo(10)
                        .documentTitle("绩效考核制度")
                        .content("Sheet: 张增波\n绩效目标确认单\n薪资结构：车间主任综合薪资 12000元/月。")
                        .score(0.89D)
                        .metadata(Map.of("title", "绩效考核制度"))
                        .build()));
        when(documentChunkMapper.selectLexicalCandidates(eq(1L), eq(10L), anyList(), anyInt()))
                .thenReturn(List.of(buildChunk(307L, 14L, 4, "考勤制度", "员工当月全勤时，全勤奖为 50 元。")));
        when(aiChatModelService.chat(any(AiChatModelRequest.class))).thenReturn(AiChatModelResponse.builder()
                .model("unit-test-chat-model")
                .content("温春雨满绩效 16000 元，全勤奖 50 元。")
                .promptTokens(20)
                .completionTokens(10)
                .totalTokens(30)
                .build());

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question(question)
                .build());

        assertFalse(response.getNoContext());
        assertTrue(response.getCitations().stream().anyMatch(citation -> citation.getQuoteText().contains("温春雨")));
        assertTrue(response.getCitations().stream().noneMatch(citation -> citation.getQuoteText().contains("张增波")));
        assertTrue(response.getCitations().stream().anyMatch(citation -> citation.getQuoteText().contains("全勤奖为 50 元")));

        ArgumentCaptor<AiChatModelRequest> chatCaptor = ArgumentCaptor.forClass(AiChatModelRequest.class);
        verify(aiChatModelService).chat(chatCaptor.capture());
        assertTrue(chatCaptor.getValue().getUserPrompt().contains("温春雨"));
        assertFalse(chatCaptor.getValue().getUserPrompt().contains("张增波"));
        assertTrue(chatCaptor.getValue().getUserPrompt().contains("全勤奖为 50 元"));
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
    void chatShouldAppendWebSearchHitsAndSkipQuestionCacheWhenEnabled() {
        mockConversationAndMessageIds();
        mockCitationId();
        String question = "Hanon Systems profile?";
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        when(aiEmbeddingService.embed(question)).thenReturn(List.of(1.0D, 0.0D));
        when(knowledgeVectorStore.search(any(KnowledgeSearchRequest.class))).thenReturn(List.of());
        when(webSearchService.search(eq(question), anyInt())).thenReturn(List.of(WebSearchResult.builder()
                .title("Hanon Systems")
                .url("https://example.com/hanon")
                .snippet("Hanon Systems is an automotive thermal management supplier.")
                .build()));
        when(aiChatModelService.chat(any(AiChatModelRequest.class))).thenReturn(AiChatModelResponse.builder()
                .model("unit-test-chat-model")
                .content("Hanon Systems is an automotive thermal management supplier.")
                .promptTokens(12)
                .completionTokens(8)
                .totalTokens(20)
                .build());

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question(question)
                .webSearchEnabled(true)
                .build());

        assertFalse(response.getNoContext());
        assertEquals(1, response.getCitations().size());
        assertEquals("联网搜索：Hanon Systems", response.getCitations().get(0).getDocumentTitle());
        verify(webSearchService).search(eq(question), anyInt());
        verify(chatQuestionCacheMapper, never()).selectLatest(any(), any(), any(), anyString());
        verify(chatQuestionCacheMapper, never()).insert(any(AiChatQuestionCacheDO.class));
    }

    @Test
    void chatShouldUseRecentConversationContextForShortFollowUp() {
        mockExistingConversationAndMessageIds();
        mockCitationId();
        String previousQuestion = "我在北侧电脑组，如果我的电脑不能联网了，应该如何排查？";
        String followUp = "二楼北电脑组";
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        when(chatMessageMapper.selectListByConversationId(500L, 1L)).thenReturn(List.of(
                buildMessage(900L, ChatMessageRoleEnum.USER.getCode(), previousQuestion),
                buildMessage(901L, ChatMessageRoleEnum.ASSISTANT.getCode(),
                        "可以按本机网线、交换机、防火墙、路由器逐级排查。")
        ));
        when(aiEmbeddingService.embed(anyString())).thenReturn(List.of(1.0D, 0.0D));
        when(knowledgeVectorStore.search(any(KnowledgeSearchRequest.class))).thenReturn(List.of(KnowledgeHit.builder()
                .tenantId(1L)
                .knowledgeBaseId(10L)
                .documentId(210L)
                .chunkId(310L)
                .chunkNo(8)
                .documentTitle("01-理文网络拓扑图")
                .content("二楼北电脑组连接二楼北东墙面板，再连接二楼北西交换机。电脑不能联网时先查本机网线、墙面板和交换机端口。")
                .score(0.92D)
                .metadata(Map.of("title", "01-理文网络拓扑图"))
                .build()));
        when(aiChatModelService.chat(any(AiChatModelRequest.class))).thenReturn(AiChatModelResponse.builder()
                .model("unit-test-chat-model")
                .content("二楼北电脑组应优先检查本机网线、墙面板和二楼北西交换机端口。")
                .promptTokens(20)
                .completionTokens(10)
                .totalTokens(30)
                .build());

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .conversationId(500L)
                .question(followUp)
                .build());

        assertFalse(response.getNoContext());
        assertTrue(response.getAnswer().contains("二楼北电脑组"));

        ArgumentCaptor<String> embeddingCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiEmbeddingService).embed(embeddingCaptor.capture());
        assertTrue(embeddingCaptor.getValue().contains(previousQuestion));
        assertTrue(embeddingCaptor.getValue().contains(followUp));

        ArgumentCaptor<AiChatModelRequest> chatCaptor = ArgumentCaptor.forClass(AiChatModelRequest.class);
        verify(aiChatModelService).chat(chatCaptor.capture());
        assertTrue(chatCaptor.getValue().getUserPrompt().contains("多轮对话上下文"));
        assertTrue(chatCaptor.getValue().getUserPrompt().contains(previousQuestion));
        assertTrue(chatCaptor.getValue().getUserPrompt().contains(followUp));

        ArgumentCaptor<AiChatMessageDO> messageCaptor = ArgumentCaptor.forClass(AiChatMessageDO.class);
        verify(chatMessageMapper, times(2)).insert(messageCaptor.capture());
        assertEquals(followUp, messageCaptor.getAllValues().get(0).getContent());
        verify(chatQuestionCacheMapper, never()).selectLatest(any(), any(), any(), anyString());
    }

    @Test
    void chatShouldCorrectClothingSizeCountWithoutCallingModel() {
        mockExistingConversationAndMessageIds();
        String previousAnswer = """
                结论：按当前知识库片段中可见且已填写尺寸的工装记录统计，共 **86 件**。

                | 工装尺寸 | 数量 |
                |---|---:|
                | S | 1 |
                | M | 5 |
                | L | 8 |
                | XL | 18 |
                | 2XL | 21 |
                | 3XL | 22 |
                | 4XL | 8 |
                | 5XL | 3 |
                | **合计** | **86** |

                其中“姬华”这一行在片段中未显示工装尺寸，未纳入统计。
                """;
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        when(chatMessageMapper.selectListByConversationId(500L, 1L)).thenReturn(List.of(
                buildMessage(900L, ChatMessageRoleEnum.USER.getCode(), "帮我统计一下工装对应尺寸的数量"),
                buildMessage(901L, ChatMessageRoleEnum.ASSISTANT.getCode(), previousAnswer)
        ));

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .conversationId(500L)
                .question("我看了一下姬华是4XL")
                .build());

        assertFalse(response.getNoContext());
        assertTrue(response.getAnswer().contains("工装合计更新为 **87 件**"));
        assertTrue(response.getAnswer().contains("| 4XL | 9 |"));
        assertTrue(response.getCitations().isEmpty());
        verifyNoInteractions(chatQuestionCacheMapper, aiEmbeddingService, knowledgeVectorStore, webSearchService,
                aiChatModelService);
    }

    @Test
    void chatShouldCountClothingSizesFromAllDocumentChunks() {
        mockConversationAndMessageIds();
        mockCitationId();
        String chunkOneContent = """
                Sheet: 工装统计表
                人员\t工装尺寸\t部门\t车间\t
                张三\tM\t生产部\t
                李四\tXL\t生产部\t
                王五\t2XL\t生产部\t""";
        String chunkTwoContent = """
                王五\t2XL\t生产部\t
                姬华\t4XL\t信息化部\t
                赵六\t4XL\t信息化部\t""";
        AiDocumentChunkDO chunkOne = buildChunk(410L, 27L, 1, "理文科技夏季工装统计表(1)", chunkOneContent);
        AiDocumentChunkDO chunkTwo = buildChunk(411L, 27L, 2, "理文科技夏季工装统计表(1)", chunkTwoContent);
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        when(documentChunkMapper.selectClothingSizeSeedCandidates(eq(1L), eq(10L), eq(50)))
                .thenReturn(List.of(chunkOne));
        when(documentChunkMapper.selectListByDocumentIdAndTenantId(27L, 10L, 1L))
                .thenReturn(List.of(chunkOne, chunkTwo));

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question("帮我统计一下工装对应尺寸的数量")
                .build());

        assertFalse(response.getNoContext());
        assertTrue(response.getAnswer().contains("共 **5 条**"));
        assertTrue(response.getAnswer().contains("| M | 1 |"));
        assertTrue(response.getAnswer().contains("| XL | 1 |"));
        assertTrue(response.getAnswer().contains("| 2XL | 1 |"));
        assertTrue(response.getAnswer().contains("| 4XL | 2 |"));
        assertTrue(response.getDebugInfo().contains("同一 documentId 下全部有效 chunk"));
        assertEquals(2, response.getCitations().size());
        verifyNoInteractions(chatQuestionCacheMapper, aiEmbeddingService, knowledgeVectorStore, webSearchService,
                aiChatModelService);
    }

    @Test
    void chatShouldReusePreviousClothingCorrectionWhenUserRetries() {
        mockExistingConversationAndMessageIds();
        String previousAnswer = """
                结论：已确认 **姬华为 4XL**。按上一轮统计口径修正后，工装合计由 **86 件** 更新为 **87 件**，其中 **4XL 由 8 件更新为 9 件**。

                | 工装尺寸 | 修正后数量 |
                |---|---:|
                | 4XL | 9 |
                | **合计** | **87** |
                """;
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        when(chatMessageMapper.selectListByConversationId(500L, 1L)).thenReturn(List.of(
                buildMessage(900L, ChatMessageRoleEnum.USER.getCode(), "我看了一下姬华是4xl"),
                buildMessage(901L, ChatMessageRoleEnum.ASSISTANT.getCode(), previousAnswer)
        ));

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .conversationId(500L)
                .question("我看了一下姬华是4XL")
                .build());

        assertFalse(response.getNoContext());
        assertEquals(previousAnswer, response.getAnswer());
        verifyNoInteractions(chatQuestionCacheMapper, aiEmbeddingService, knowledgeVectorStore, webSearchService,
                aiChatModelService);
    }

    @Test
    void chatShouldExpandFullStructuredDocumentForStatisticalQuestion() {
        mockConversationAndMessageIds();
        mockCitationId();
        String question = "帮我统计废品表的合计数量";
        String firstChunkContent = """
                Sheet: 废品统计表
                项目\t数量
                A\t1
                B\t2""";
        String secondChunkContent = """
                B\t2
                C\t3""";
        AiDocumentChunkDO firstChunk = buildChunk(510L, 30L, 1, "废品统计表", firstChunkContent);
        AiDocumentChunkDO secondChunk = buildChunk(511L, 30L, 2, "废品统计表", secondChunkContent);
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        when(aiEmbeddingService.embed(question)).thenReturn(List.of(1.0D, 0.0D));
        when(knowledgeVectorStore.search(any(KnowledgeSearchRequest.class))).thenReturn(List.of(KnowledgeHit.builder()
                .tenantId(1L)
                .knowledgeBaseId(10L)
                .documentId(30L)
                .chunkId(510L)
                .chunkNo(1)
                .documentTitle("废品统计表")
                .content(firstChunkContent)
                .score(0.91D)
                .metadata(Map.of("title", "废品统计表", "source", Map.of("parser", "ExcelDocumentParser")))
                .build()));
        when(documentChunkMapper.selectListByDocumentIdAndTenantId(30L, 10L, 1L))
                .thenReturn(List.of(firstChunk, secondChunk));
        when(aiChatModelService.chat(any(AiChatModelRequest.class))).thenReturn(AiChatModelResponse.builder()
                .model("unit-test-chat-model")
                .content("废品数量合计为 6。")
                .promptTokens(20)
                .completionTokens(6)
                .totalTokens(26)
                .build());

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question(question)
                .build());

        assertFalse(response.getNoContext());
        assertEquals("废品数量合计为 6。", response.getAnswer());
        assertEquals(1, response.getCitations().size());
        assertEquals(30L, response.getCitations().get(0).getDocumentId());

        ArgumentCaptor<AiChatModelRequest> chatCaptor = ArgumentCaptor.forClass(AiChatModelRequest.class);
        verify(aiChatModelService).chat(chatCaptor.capture());
        assertTrue(chatCaptor.getValue().getUserPrompt().contains("C\t3"));
        assertEquals(1, chatCaptor.getValue().getUserPrompt().split("B\t2", -1).length - 1);
        verify(documentChunkMapper).selectListByDocumentIdAndTenantId(30L, 10L, 1L);
    }

    @Test
    void chatShouldSearchAllAccessibleKnowledgeBasesWhenRequestAll() {
        mockConversationAndMessageIds();
        mockCitationId();
        AiKnowledgeBaseDO firstKnowledge = buildKnowledge(10L, "富通天下测试", "*");
        AiKnowledgeBaseDO secondKnowledge = buildKnowledge(20L, "n8n", "*");
        when(knowledgeBaseMapper.selectListByTenantId(1L)).thenReturn(List.of(firstKnowledge, secondKnowledge));
        when(aiEmbeddingService.embed("现有技术栈有哪些？")).thenReturn(List.of(1.0D, 0.0D));
        when(knowledgeVectorStore.search(any(KnowledgeSearchRequest.class))).thenAnswer(invocation -> {
            KnowledgeSearchRequest searchRequest = invocation.getArgument(0);
            if (searchRequest.getKnowledgeBaseId().equals(10L)) {
                return List.of(KnowledgeHit.builder()
                        .tenantId(1L)
                        .knowledgeBaseId(10L)
                        .documentId(200L)
                        .chunkId(300L)
                        .chunkNo(1)
                        .documentTitle("技术文档")
                        .content("后端技术栈包含 Java、Spring Boot 和 MyBatis Plus。")
                        .score(0.91D)
                        .metadata(Map.of("title", "技术文档"))
                        .build());
            }
            return List.of(KnowledgeHit.builder()
                    .tenantId(1L)
                    .knowledgeBaseId(20L)
                    .documentId(201L)
                    .chunkId(301L)
                    .chunkNo(1)
                    .documentTitle("n8n 接入说明")
                    .content("n8n Webhook 可作为 API 数据源写入知识库。")
                    .score(0.88D)
                    .metadata(Map.of("title", "n8n 接入说明"))
                    .build());
        });
        when(aiChatModelService.chat(any(AiChatModelRequest.class))).thenReturn(AiChatModelResponse.builder()
                .model("unit-test-chat-model")
                .content("现有技术栈包含 Java、Spring Boot、MyBatis Plus，并接入 n8n。")
                .promptTokens(20)
                .completionTokens(10)
                .totalTokens(30)
                .build());

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(0L)
                .question("现有技术栈有哪些？")
                .build());

        assertFalse(response.getNoContext());
        assertEquals(2, response.getCitations().size());
        assertTrue(response.getCitations().stream().anyMatch(citation -> citation.getKnowledgeBaseId().equals(10L)));
        assertTrue(response.getCitations().stream().anyMatch(citation -> citation.getKnowledgeBaseId().equals(20L)));

        ArgumentCaptor<KnowledgeSearchRequest> searchCaptor = ArgumentCaptor.forClass(KnowledgeSearchRequest.class);
        verify(knowledgeVectorStore, times(2)).search(searchCaptor.capture());
        assertEquals(List.of(10L, 20L), searchCaptor.getAllValues().stream()
                .map(KnowledgeSearchRequest::getKnowledgeBaseId)
                .toList());
        assertTrue(searchCaptor.getAllValues().stream()
                .allMatch(request -> request.getTenantId().equals(1L) && request.getDepartmentId().equals(20L)));
        verify(chatQuestionCacheMapper, never()).selectLatest(any(), any(), any(), anyString());
    }

    @Test
    void chatShouldAnswerTableInventoryDirectlyFromParsedChunks() {
        mockConversationAndMessageIds();
        mockCitationId();
        String question = "目前 MES 里面都有哪些数据表？";
        when(knowledgeBaseMapper.selectByIdAndTenantId(10L, 1L)).thenReturn(buildKnowledge("*"));
        when(documentChunkMapper.selectTableInventoryCandidates(eq(1L), eq(10L), eq(500)))
                .thenReturn(List.of(
                        buildChunk(401L, 24L, 1, "MES 技术说明",
                                "表：a_banci\n字段名\t类型\nid\tint\n\n表：a_workorder\n字段名\t类型\nid\tint"),
                        buildChunk(402L, 24L, 2, "MES 技术说明",
                                "表：mgy_device\n字段名\t类型\nid\tint\n\n表：a_banci\n字段名\t类型\nid\tint")
                ));

        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(10L)
                .question(question)
                .build());

        assertFalse(response.getNoContext());
        assertTrue(response.getAnswer().contains("共 3 张"));
        assertTrue(response.getAnswer().contains("`a_banci`"));
        assertTrue(response.getAnswer().contains("`a_workorder`"));
        assertTrue(response.getAnswer().contains("`mgy_device`"));
        assertEquals(3, response.getCitations().size());
        assertTrue(response.getDebugInfo().contains("跳过通用 topK 向量召回"));
        verifyNoInteractions(chatQuestionCacheMapper, aiEmbeddingService, knowledgeVectorStore, webSearchService,
                aiChatModelService);
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

    private void mockExistingConversationAndMessageIds() {
        when(chatConversationMapper.selectByIdAndTenantIdAndDepartmentIdAndUserId(500L, 1L, 20L, 100L))
                .thenReturn(AiChatConversationDO.builder()
                        .id(500L)
                        .tenantId(1L)
                        .departmentId(20L)
                        .knowledgeBaseId(10L)
                        .userId(100L)
                        .title("网络排查")
                        .build());
        AtomicLong messageId = new AtomicLong(1000L);
        when(chatMessageMapper.insert(any(AiChatMessageDO.class))).thenAnswer(invocation -> {
            AiChatMessageDO message = invocation.getArgument(0);
            message.setId(messageId.getAndIncrement());
            return 1;
        });
        when(chatConversationMapper.updateLastMessageTime(eq(500L), eq(1L), eq(20L), any())).thenReturn(1);
    }

    private AiKnowledgeBaseDO buildKnowledge(String departmentIds) {
        return buildKnowledge(10L, "财务知识库", departmentIds);
    }

    private AiKnowledgeBaseDO buildKnowledge(Long id, String name, String departmentIds) {
        return AiKnowledgeBaseDO.builder()
                .id(id)
                .tenantId(1L)
                .departmentIds(departmentIds)
                .name(name)
                .topK(3)
                .build();
    }

    private AiDocumentChunkDO buildChunk(Long id, Long documentId, Integer chunkIndex, String title, String content) {
        return AiDocumentChunkDO.builder()
                .id(id)
                .tenantId(1L)
                .knowledgeBaseId(10L)
                .documentId(documentId)
                .chunkIndex(chunkIndex)
                .content(content)
                .metadataJson("{\"title\":\"" + title + "\"}")
                .status(10)
                .build();
    }

    private AiChatMessageDO buildMessage(Long id, String role, String content) {
        return AiChatMessageDO.builder()
                .id(id)
                .tenantId(1L)
                .departmentId(20L)
                .conversationId(500L)
                .userId(100L)
                .role(role)
                .content(content)
                .build();
    }

}
