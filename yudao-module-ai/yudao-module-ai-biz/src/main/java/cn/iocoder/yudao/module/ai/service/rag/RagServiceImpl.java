package cn.iocoder.yudao.module.ai.service.rag;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatMessageDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatCitationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatConversationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatMessageMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiKnowledgeBaseMapper;
import cn.iocoder.yudao.module.ai.enums.ChatMessageRoleEnum;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeSearchRequest;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVectorStore;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelRequest;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.embedding.AiEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_CONVERSATION_ACCESS_DENIED;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_CONVERSATION_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_KNOWLEDGE_ACCESS_DENIED;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_KNOWLEDGE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_REQUEST_INVALID;

/**
 * RAG 问答服务实现。
 *
 * <p>第一阶段只实现基础向量召回和模型问答，不做 query rewrite、rerank、hybrid search、agent。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private static final String ALL_DEPARTMENTS = "*";
    private static final String FALLBACK_ANSWER = "根据当前知识库资料无法确认";
    private static final int DEFAULT_STATUS = 0;
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_SCORE_THRESHOLD = 0.7D;
    private static final int TITLE_MAX_LENGTH = 64;
    private static final int QUOTE_TEXT_MAX_LENGTH = 2000;

    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final AiChatConversationMapper chatConversationMapper;
    private final AiChatMessageMapper chatMessageMapper;
    private final AiChatCitationMapper chatCitationMapper;
    private final AiEmbeddingService aiEmbeddingService;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final PromptBuilder promptBuilder;
    private final AiChatModelService aiChatModelService;
    private final AiProperties aiProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagChatResponse chat(RagChatRequest request) {
        validateRequest(request);
        long startNanos = System.nanoTime();
        Long tenantId = AiUserContextHolder.getTenantId();
        Long departmentId = AiUserContextHolder.getDepartmentId();
        Long userId = AiUserContextHolder.getUserId();

        AiKnowledgeBaseDO knowledgeBase = validateKnowledgeAccessible(request.getKnowledgeBaseId(), tenantId, departmentId);
        AiChatConversationDO conversation = getOrCreateConversation(request, tenantId, departmentId, userId);
        AiChatMessageDO userMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.USER.getCode(), request.getQuestion(), null, 0L);

        List<Double> queryEmbedding = aiEmbeddingService.embed(request.getQuestion());
        KnowledgeSearchRequest searchRequest = KnowledgeSearchRequest.builder()
                .tenantId(tenantId)
                .departmentId(departmentId)
                .knowledgeBaseId(knowledgeBase.getId())
                .queryEmbedding(queryEmbedding)
                .topK(resolveTopK(request, knowledgeBase))
                .scoreThreshold(resolveScoreThreshold(request, knowledgeBase))
                .build();
        List<KnowledgeHit> hits = knowledgeVectorStore.search(searchRequest);
        if (hits == null || hits.isEmpty()) {
            RagChatResponse response = saveFallbackAnswer(conversation, userMessage, tenantId, departmentId, userId);
            log.info("RAG chat no context, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, elapsedMs={}",
                    tenantId, departmentId, knowledgeBase.getId(), conversation.getId(), elapsedMillis(startNanos));
            return response;
        }

        PromptBuildResult prompt = promptBuilder.build(request.getQuestion(), hits);
        if (prompt.isNoContext()) {
            RagChatResponse response = saveFallbackAnswer(conversation, userMessage, tenantId, departmentId, userId);
            log.info("RAG chat no effective context, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, hitCount={}, elapsedMs={}",
                    tenantId, departmentId, knowledgeBase.getId(), conversation.getId(), hits.size(), elapsedMillis(startNanos));
            return response;
        }

        long modelStartNanos = System.nanoTime();
        AiChatModelResponse modelResponse = aiChatModelService.chat(AiChatModelRequest.builder()
                .model(knowledgeBase.getChatModel())
                .systemPrompt(prompt.getSystemPrompt())
                .userPrompt(prompt.getUserPrompt())
                .build());
        long modelLatencyMs = elapsedMillis(modelStartNanos);
        String answer = modelResponse.getContent() == null || modelResponse.getContent().isBlank()
                ? FALLBACK_ANSWER : modelResponse.getContent();
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), answer, modelResponse, modelLatencyMs);
        List<RagChatCitation> citations = saveCitations(tenantId, departmentId, assistantMessage.getId(),
                knowledgeBase.getId(), prompt.getKnowledgeHits());
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);

        log.info("RAG chat success, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, hitCount={}, citationCount={}, elapsedMs={}",
                tenantId, departmentId, knowledgeBase.getId(), conversation.getId(), hits.size(), citations.size(),
                elapsedMillis(startNanos));
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(answer)
                .noContext(false)
                .citations(citations)
                .build();
    }

    private void validateRequest(RagChatRequest request) {
        if (request == null || request.getKnowledgeBaseId() == null
                || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new ServiceException(RAG_REQUEST_INVALID, "RAG 问答参数不完整");
        }
    }

    private AiKnowledgeBaseDO validateKnowledgeAccessible(Long knowledgeBaseId, Long tenantId, Long departmentId) {
        AiKnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectByIdAndTenantId(knowledgeBaseId, tenantId);
        if (knowledgeBase == null) {
            throw new ServiceException(RAG_KNOWLEDGE_NOT_EXISTS, "知识库不存在");
        }
        if (!isDepartmentAllowed(knowledgeBase.getDepartmentIds(), departmentId)) {
            throw new ServiceException(RAG_KNOWLEDGE_ACCESS_DENIED, "无权访问该知识库");
        }
        return knowledgeBase;
    }

    private boolean isDepartmentAllowed(String departmentIds, Long departmentId) {
        if (departmentId == null) {
            return false;
        }
        if (departmentIds == null || departmentIds.isBlank()) {
            return false;
        }
        for (String item : departmentIds.split(",")) {
            String normalizedItem = item.trim();
            if (ALL_DEPARTMENTS.equals(normalizedItem) || String.valueOf(departmentId).equals(normalizedItem)) {
                return true;
            }
        }
        return false;
    }

    private AiChatConversationDO getOrCreateConversation(RagChatRequest request, Long tenantId, Long departmentId,
                                                         Long userId) {
        if (request.getConversationId() != null) {
            AiChatConversationDO conversation = chatConversationMapper.selectByIdAndTenantIdAndDepartmentIdAndUserId(
                    request.getConversationId(), tenantId, departmentId, userId);
            if (conversation == null) {
                throw new ServiceException(RAG_CONVERSATION_NOT_EXISTS, "会话不存在");
            }
            if (!request.getKnowledgeBaseId().equals(conversation.getKnowledgeBaseId())) {
                throw new ServiceException(RAG_CONVERSATION_ACCESS_DENIED, "会话不属于当前知识库");
            }
            return conversation;
        }
        AiChatConversationDO conversation = AiChatConversationDO.builder()
                .tenantId(tenantId)
                .departmentId(departmentId)
                .knowledgeBaseId(request.getKnowledgeBaseId())
                .userId(userId)
                .title(buildConversationTitle(request.getQuestion()))
                .status(DEFAULT_STATUS)
                .lastMessageTime(LocalDateTime.now())
                .build();
        chatConversationMapper.insert(conversation);
        return conversation;
    }

    private AiChatMessageDO saveMessage(Long tenantId, Long departmentId, Long conversationId, Long userId, String role,
                                        String content, AiChatModelResponse modelResponse, Long latencyMs) {
        AiChatMessageDO message = AiChatMessageDO.builder()
                .tenantId(tenantId)
                .departmentId(departmentId)
                .conversationId(conversationId)
                .userId(userId)
                .role(role)
                .content(content)
                .model(modelResponse == null ? null : modelResponse.getModel())
                .promptTokens(modelResponse == null || modelResponse.getPromptTokens() == null ? 0 : modelResponse.getPromptTokens())
                .completionTokens(modelResponse == null || modelResponse.getCompletionTokens() == null ? 0 : modelResponse.getCompletionTokens())
                .totalTokens(modelResponse == null || modelResponse.getTotalTokens() == null ? 0 : modelResponse.getTotalTokens())
                .latencyMs(latencyMs == null ? 0L : latencyMs)
                .status(DEFAULT_STATUS)
                .build();
        chatMessageMapper.insert(message);
        return message;
    }

    private RagChatResponse saveFallbackAnswer(AiChatConversationDO conversation, AiChatMessageDO userMessage,
                                               Long tenantId, Long departmentId, Long userId) {
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), FALLBACK_ANSWER, null, 0L);
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(FALLBACK_ANSWER)
                .noContext(true)
                .citations(Collections.emptyList())
                .build();
    }

    private List<RagChatCitation> saveCitations(Long tenantId, Long departmentId, Long assistantMessageId,
                                                Long knowledgeBaseId, List<KnowledgeHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<RagChatCitation> citations = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            KnowledgeHit hit = hits.get(i);
            AiChatCitationDO citation = AiChatCitationDO.builder()
                    .tenantId(tenantId)
                    .departmentId(departmentId)
                    .messageId(assistantMessageId)
                    .knowledgeBaseId(knowledgeBaseId)
                    .documentId(hit.getDocumentId())
                    .chunkId(hit.getChunkId())
                    .documentTitle(hit.getDocumentTitle())
                    .score(toBigDecimal(hit.getScore()))
                    .sortOrder(i + 1)
                    .contentSnapshot(truncate(hit.getContent(), QUOTE_TEXT_MAX_LENGTH))
                    .quoteText(truncate(hit.getContent(), QUOTE_TEXT_MAX_LENGTH))
                    .build();
            chatCitationMapper.insert(citation);
            citations.add(RagChatCitation.builder()
                    .documentId(hit.getDocumentId())
                    .chunkId(hit.getChunkId())
                    .chunkNo(hit.getChunkNo())
                    .documentTitle(hit.getDocumentTitle())
                    .score(hit.getScore())
                    .quoteText(citation.getQuoteText())
                    .build());
        }
        return citations;
    }

    private Integer resolveTopK(RagChatRequest request, AiKnowledgeBaseDO knowledgeBase) {
        if (request.getTopK() != null && request.getTopK() > 0) {
            return request.getTopK();
        }
        if (knowledgeBase.getTopK() != null && knowledgeBase.getTopK() > 0) {
            return knowledgeBase.getTopK();
        }
        Integer defaultTopK = aiProperties.getRag() == null ? null : aiProperties.getRag().getDefaultTopK();
        return defaultTopK == null || defaultTopK <= 0 ? DEFAULT_TOP_K : defaultTopK;
    }

    private Double resolveScoreThreshold(RagChatRequest request, AiKnowledgeBaseDO knowledgeBase) {
        if (request.getScoreThreshold() != null) {
            return request.getScoreThreshold();
        }
        if (knowledgeBase.getScoreThreshold() != null) {
            return knowledgeBase.getScoreThreshold();
        }
        Double defaultScoreThreshold = aiProperties.getRag() == null ? null : aiProperties.getRag().getDefaultScoreThreshold();
        return defaultScoreThreshold == null ? DEFAULT_SCORE_THRESHOLD : defaultScoreThreshold;
    }

    private void updateConversationLastMessageTime(Long conversationId, Long tenantId, Long departmentId) {
        chatConversationMapper.updateLastMessageTime(conversationId, tenantId, departmentId, LocalDateTime.now());
    }

    private String buildConversationTitle(String question) {
        return truncate(question.trim(), TITLE_MAX_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

}
