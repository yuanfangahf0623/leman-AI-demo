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
import cn.iocoder.yudao.module.ai.enums.KnowledgeVisibilityEnum;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeSearchRequest;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVectorStore;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelRequest;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.embedding.AiEmbeddingService;
import cn.iocoder.yudao.module.ai.service.rag.sensitive.PersonalSensitiveDataPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_CONVERSATION_ACCESS_DENIED;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_CONVERSATION_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_KNOWLEDGE_ACCESS_DENIED;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_KNOWLEDGE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_PERSONAL_SENSITIVE_ACCESS_DENIED;
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
    private static final String QUESTION_CACHE_VERSION = "rag-v2-user-context-multi-hit";
    private static final int DEFAULT_STATUS = 0;
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_SCORE_THRESHOLD = 0.1D;
    private static final int TITLE_MAX_LENGTH = 64;
    private static final int QUOTE_TEXT_MAX_LENGTH = 2000;
    private static final int LEXICAL_FALLBACK_MULTIPLIER = 6;
    private static final int LEXICAL_RESULT_MULTIPLIER = 2;
    private static final int LEXICAL_KEYWORD_LIMIT = 12;
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<RagChatCitation>> RAG_CITATION_LIST_TYPE = new TypeReference<>() {
    };
    private static final Pattern ASCII_WORD_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9.+#-]{1,}");
    private static final Set<String> QUERY_STOP_WORDS = Set.of("目前", "现在", "现有", "当前", "请问", "哪些", "什么",
            "是什么", "有哪些", "有那些", "多少", "如何", "怎么", "可以", "一下", "如果", "情况下", "的情况下");
    private static final Set<String> SELF_IDENTITY_QUESTIONS = Set.of("我是谁", "请问我是谁", "我叫什么",
            "我叫什么名字", "我的名字是什么", "本人是谁", "当前用户是谁", "登录用户是谁");
    private static final List<String> DOMAIN_KEYWORDS = List.of("前端", "后端", "技术栈", "技术", "Vue", "Vite",
            "TypeScript", "Element", "Element Plus", "pnpm", "Java", "Spring", "MyBatis", "MySQL",
            "PostgreSQL", "pgvector", "Qdrant", "Redis", "Nacos", "MinIO", "RabbitMQ", "RAG", "Embedding",
            "考勤", "全勤", "全勤奖", "绩效", "满绩效", "工资", "薪资", "奖金", "补贴", "岗位",
            "车间主任", "应发", "实发", "金额");
    private static final List<List<String>> INTENT_COVERAGE_KEYWORD_GROUPS = List.of(
            List.of("考勤", "全勤", "全勤奖", "出勤", "缺勤", "迟到", "早退"),
            List.of("绩效", "满绩效", "绩效工资", "绩效奖金"),
            List.of("工资", "薪资", "奖金", "补贴", "金额", "多少钱"),
            List.of("车间主任", "岗位", "职务")
    );

    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final AiChatConversationMapper chatConversationMapper;
    private final AiChatMessageMapper chatMessageMapper;
    private final AiChatCitationMapper chatCitationMapper;
    private final AiChatQuestionCacheMapper chatQuestionCacheMapper;
    private final AiDocumentChunkMapper documentChunkMapper;
    private final AiEmbeddingService aiEmbeddingService;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final PromptBuilder promptBuilder;
    private final AiChatModelService aiChatModelService;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;
    private final PersonalSensitiveDataPolicy personalSensitiveDataPolicy;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagChatResponse chat(RagChatRequest request) {
        validateRequest(request);
        long startNanos = System.nanoTime();
        Long tenantId = AiUserContextHolder.getTenantId();
        Long departmentId = AiUserContextHolder.getDepartmentId();
        Long userId = AiUserContextHolder.getUserId();
        String currentUserNickname = AiUserContextHolder.getNickname();
        boolean admin = AiUserContextHolder.isAdmin();

        AiKnowledgeBaseDO knowledgeBase = validateKnowledgeAccessible(request.getKnowledgeBaseId(), tenantId, departmentId);
        String normalizedQuestion = normalizeQuestion(request.getQuestion());
        boolean personalSensitive = personalSensitiveDataPolicy.isSensitiveQuestion(request.getQuestion(), normalizedQuestion);
        validatePersonalSensitiveQuestionAccess(request.getQuestion(), normalizedQuestion, currentUserNickname, admin,
                personalSensitive);
        AiChatConversationDO conversation = getOrCreateConversation(request, tenantId, departmentId, userId);
        AiChatMessageDO userMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.USER.getCode(), request.getQuestion(), null, 0L);

        if (isSelfIdentityQuestion(normalizedQuestion)) {
            return saveCurrentUserIdentityAnswer(conversation, userMessage, tenantId, departmentId, userId,
                    currentUserNickname, startNanos);
        }

        String questionHash = sha256Hex(QUESTION_CACHE_VERSION + ":" + normalizedQuestion);
        boolean userContextSensitive = isUserContextSensitiveQuestion(normalizedQuestion);
        boolean cacheableQuestion = !userContextSensitive && !personalSensitive;
        if (cacheableQuestion) {
            AiChatQuestionCacheDO cachedAnswer = chatQuestionCacheMapper.selectLatest(tenantId, departmentId,
                    knowledgeBase.getId(), questionHash);
            if (cachedAnswer != null && cachedAnswer.getAnswer() != null && !cachedAnswer.getAnswer().isBlank()) {
                return saveCachedAnswer(conversation, userMessage, tenantId, departmentId, userId, knowledgeBase,
                        cachedAnswer, startNanos);
            }
        }

        List<KnowledgeHit> hits = searchKnowledge(request, knowledgeBase, tenantId, departmentId);
        if (personalSensitive && !admin) {
            hits = filterPersonalSensitiveHitsForCurrentUser(hits, currentUserNickname);
        }
        int hitCount = hits == null ? 0 : hits.size();
        PromptBuildResult prompt = promptBuilder.build(request.getQuestion(), hits, currentUserNickname);
        if (prompt.isNoContext()) {
            RagChatResponse response = saveFallbackAnswer(conversation, userMessage, tenantId, departmentId, userId,
                    prompt.getDebugInfo());
            log.info("RAG chat no effective context, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, hitCount={}, elapsedMs={}",
                    tenantId, departmentId, knowledgeBase.getId(), conversation.getId(), hitCount, elapsedMillis(startNanos));
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
        if (cacheableQuestion) {
            saveQuestionCache(tenantId, departmentId, userId, knowledgeBase.getId(), request.getQuestion(),
                    normalizedQuestion, questionHash, answer, modelResponse, modelLatencyMs, citations);
        }
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);

        log.info("RAG chat success, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, hitCount={}, citationCount={}, elapsedMs={}",
                tenantId, departmentId, knowledgeBase.getId(), conversation.getId(), hitCount, citations.size(),
                elapsedMillis(startNanos));
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(answer)
                .noContext(false)
                .debugInfo(prompt.getDebugInfo())
                .citations(citations)
                .build();
    }

    private void validateRequest(RagChatRequest request) {
        if (request == null || request.getKnowledgeBaseId() == null
                || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new ServiceException(RAG_REQUEST_INVALID, "RAG 问答参数不完整");
        }
    }

    private void validatePersonalSensitiveQuestionAccess(String question, String normalizedQuestion,
                                                         String currentUserNickname, boolean admin,
                                                         boolean personalSensitive) {
        if (!personalSensitive || admin) {
            return;
        }
        if (currentUserNickname == null || currentUserNickname.isBlank()) {
            throw new ServiceException(RAG_PERSONAL_SENSITIVE_ACCESS_DENIED,
                    personalSensitiveDataPolicy.getAccessDeniedMessage());
        }
        if (isUserContextSensitiveQuestion(normalizedQuestion)
                || containsIgnoreCase(question, currentUserNickname)
                || normalizeQuestion(question).contains(normalizeQuestion(currentUserNickname))) {
            return;
        }
        throw new ServiceException(RAG_PERSONAL_SENSITIVE_ACCESS_DENIED,
                personalSensitiveDataPolicy.getAccessDeniedMessage());
    }

    private AiKnowledgeBaseDO validateKnowledgeAccessible(Long knowledgeBaseId, Long tenantId, Long departmentId) {
        AiKnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectByIdAndTenantId(knowledgeBaseId, tenantId);
        if (knowledgeBase == null) {
            throw new ServiceException(RAG_KNOWLEDGE_NOT_EXISTS, "知识库不存在");
        }
        if (!isDepartmentAllowed(knowledgeBase, departmentId)) {
            throw new ServiceException(RAG_KNOWLEDGE_ACCESS_DENIED, "无权访问该知识库");
        }
        return knowledgeBase;
    }

    private boolean isDepartmentAllowed(AiKnowledgeBaseDO knowledgeBase, Long departmentId) {
        if (KnowledgeVisibilityEnum.PUBLIC.getCode().equals(knowledgeBase.getVisibility())) {
            return true;
        }
        if (departmentId == null) {
            return false;
        }
        String departmentIds = knowledgeBase.getDepartmentIds();
        if (departmentIds == null || departmentIds.isBlank() || ALL_DEPARTMENTS.equals(departmentIds.trim())) {
            return true;
        }
        String normalizedDepartmentIds = departmentIds.replace("[", "")
                .replace("]", "")
                .replace("\"", "");
        for (String item : normalizedDepartmentIds.split(",")) {
            String normalizedItem = item.trim();
            if (normalizedItem.isBlank()) {
                continue;
            }
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
                                               Long tenantId, Long departmentId, Long userId, String debugInfo) {
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), FALLBACK_ANSWER, null, 0L);
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(FALLBACK_ANSWER)
                .noContext(true)
                .debugInfo(debugInfo)
                .citations(Collections.emptyList())
                .build();
    }

    private RagChatResponse saveCurrentUserIdentityAnswer(AiChatConversationDO conversation, AiChatMessageDO userMessage,
                                                          Long tenantId, Long departmentId, Long userId,
                                                          String currentUserNickname, long startNanos) {
        String answer = buildCurrentUserIdentityAnswer(currentUserNickname, userId);
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), answer, null, 0L);
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);
        log.info("RAG chat answered by login user context, tenantId={}, departmentId={}, conversationId={}, elapsedMs={}",
                tenantId, departmentId, conversation.getId(), elapsedMillis(startNanos));
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(answer)
                .noContext(false)
                .debugInfo("命中当前登录用户身份问题：答案来自登录态用户姓名，跳过 embedding、向量检索和模型调用。")
                .citations(Collections.emptyList())
                .build();
    }

    private String buildCurrentUserIdentityAnswer(String currentUserNickname, Long userId) {
        if (currentUserNickname != null && !currentUserNickname.isBlank()) {
            return "你是" + currentUserNickname.trim() + "。";
        }
        return "你是当前登录用户（用户 ID：" + userId + "）。";
    }

    private RagChatResponse saveCachedAnswer(AiChatConversationDO conversation, AiChatMessageDO userMessage,
                                             Long tenantId, Long departmentId, Long userId,
                                             AiKnowledgeBaseDO knowledgeBase, AiChatQuestionCacheDO cachedAnswer,
                                             long startNanos) {
        AiChatModelResponse cachedModelResponse = AiChatModelResponse.builder()
                .model(cachedAnswer.getModel())
                .promptTokens(cachedAnswer.getPromptTokens())
                .completionTokens(cachedAnswer.getCompletionTokens())
                .totalTokens(cachedAnswer.getTotalTokens())
                .build();
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), cachedAnswer.getAnswer(), cachedModelResponse, 0L);
        List<RagChatCitation> citations = saveCachedCitations(tenantId, departmentId, assistantMessage.getId(),
                knowledgeBase.getId(), cachedAnswer);
        chatQuestionCacheMapper.updateHitCount(cachedAnswer.getId(), tenantId, departmentId);
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);
        log.info("RAG question cache hit, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, cacheId={}, citationCount={}, elapsedMs={}",
                tenantId, departmentId, knowledgeBase.getId(), conversation.getId(), cachedAnswer.getId(),
                citations.size(), elapsedMillis(startNanos));
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(cachedAnswer.getAnswer())
                .noContext(false)
                .debugInfo("问题库命中：跳过 embedding、向量检索和模型调用。")
                .citations(citations)
                .build();
    }

    private List<RagChatCitation> saveCachedCitations(Long tenantId, Long departmentId, Long assistantMessageId,
                                                      Long knowledgeBaseId, AiChatQuestionCacheDO cachedAnswer) {
        List<RagChatCitation> cachedCitations = parseCachedCitations(cachedAnswer);
        if (cachedCitations.isEmpty()) {
            return Collections.emptyList();
        }
        List<RagChatCitation> citations = new ArrayList<>(cachedCitations.size());
        for (int i = 0; i < cachedCitations.size(); i++) {
            RagChatCitation cachedCitation = cachedCitations.get(i);
            AiChatCitationDO citation = AiChatCitationDO.builder()
                    .tenantId(tenantId)
                    .departmentId(departmentId)
                    .messageId(assistantMessageId)
                    .knowledgeBaseId(knowledgeBaseId)
                    .documentId(cachedCitation.getDocumentId())
                    .chunkId(cachedCitation.getChunkId())
                    .documentTitle(cachedCitation.getDocumentTitle())
                    .score(toBigDecimal(cachedCitation.getScore()))
                    .sortOrder(i + 1)
                    .contentSnapshot(truncate(cachedCitation.getQuoteText(), QUOTE_TEXT_MAX_LENGTH))
                    .quoteText(truncate(cachedCitation.getQuoteText(), QUOTE_TEXT_MAX_LENGTH))
                    .build();
            chatCitationMapper.insert(citation);
            citations.add(RagChatCitation.builder()
                    .documentId(cachedCitation.getDocumentId())
                    .chunkId(cachedCitation.getChunkId())
                    .chunkNo(cachedCitation.getChunkNo())
                    .documentTitle(cachedCitation.getDocumentTitle())
                    .score(cachedCitation.getScore())
                    .quoteText(citation.getQuoteText())
                    .build());
        }
        return citations;
    }

    private List<RagChatCitation> parseCachedCitations(AiChatQuestionCacheDO cachedAnswer) {
        if (cachedAnswer.getCitationSnapshotJson() == null || cachedAnswer.getCitationSnapshotJson().isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(cachedAnswer.getCitationSnapshotJson(), RAG_CITATION_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            log.warn("RAG question cache citation parse failed, tenantId={}, knowledgeBaseId={}, cacheId={}",
                    cachedAnswer.getTenantId(), cachedAnswer.getKnowledgeBaseId(), cachedAnswer.getId(), ex);
            return Collections.emptyList();
        }
    }

    private void saveQuestionCache(Long tenantId, Long departmentId, Long userId, Long knowledgeBaseId,
                                   String question, String normalizedQuestion, String questionHash, String answer,
                                   AiChatModelResponse modelResponse, Long latencyMs,
                                   List<RagChatCitation> citations) {
        if (FALLBACK_ANSWER.equals(answer) || citations == null || citations.isEmpty()) {
            return;
        }
        String citationSnapshotJson = serializeCitations(tenantId, knowledgeBaseId, citations);
        if (citationSnapshotJson == null) {
            return;
        }
        AiChatQuestionCacheDO oldCache = chatQuestionCacheMapper.selectLatest(tenantId, departmentId, knowledgeBaseId,
                questionHash);
        if (oldCache != null) {
            chatQuestionCacheMapper.updateAnswer(oldCache.getId(), tenantId, departmentId, question,
                    normalizedQuestion, answer, modelResponse == null ? null : modelResponse.getModel(),
                    modelResponse == null ? 0 : modelResponse.getPromptTokens(),
                    modelResponse == null ? 0 : modelResponse.getCompletionTokens(),
                    modelResponse == null ? 0 : modelResponse.getTotalTokens(),
                    latencyMs, citationSnapshotJson);
            return;
        }
        chatQuestionCacheMapper.insert(AiChatQuestionCacheDO.builder()
                .tenantId(tenantId)
                .departmentId(departmentId)
                .knowledgeBaseId(knowledgeBaseId)
                .userId(userId)
                .question(question)
                .normalizedQuestion(normalizedQuestion)
                .questionHash(questionHash)
                .answer(answer)
                .model(modelResponse == null ? null : modelResponse.getModel())
                .promptTokens(modelResponse == null || modelResponse.getPromptTokens() == null
                        ? 0 : modelResponse.getPromptTokens())
                .completionTokens(modelResponse == null || modelResponse.getCompletionTokens() == null
                        ? 0 : modelResponse.getCompletionTokens())
                .totalTokens(modelResponse == null || modelResponse.getTotalTokens() == null
                        ? 0 : modelResponse.getTotalTokens())
                .latencyMs(latencyMs == null ? 0L : latencyMs)
                .citationSnapshotJson(citationSnapshotJson)
                .hitCount(0)
                .status(DEFAULT_STATUS)
                .build());
    }

    private String serializeCitations(Long tenantId, Long knowledgeBaseId, List<RagChatCitation> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException ex) {
            log.warn("RAG question cache citation serialize failed, tenantId={}, knowledgeBaseId={}, citationCount={}",
                    tenantId, knowledgeBaseId, citations.size(), ex);
            return null;
        }
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

    private List<KnowledgeHit> searchKnowledge(RagChatRequest request, AiKnowledgeBaseDO knowledgeBase,
                                               Long tenantId, Long departmentId) {
        List<Double> queryEmbedding = aiEmbeddingService.embed(request.getQuestion());
        KnowledgeSearchRequest searchRequest = KnowledgeSearchRequest.builder()
                .tenantId(tenantId)
                .departmentId(departmentId)
                .knowledgeBaseId(knowledgeBase.getId())
                .queryEmbedding(queryEmbedding)
                .topK(resolveTopK(request, knowledgeBase))
                .scoreThreshold(resolveScoreThreshold(request, knowledgeBase))
                .build();
        List<KnowledgeHit> semanticHits = knowledgeVectorStore.search(searchRequest);
        List<KnowledgeHit> lexicalHits = lexicalFallbackSearch(request.getQuestion(), searchRequest);
        return mergeKnowledgeHits(semanticHits, lexicalHits);
    }

    private List<KnowledgeHit> mergeKnowledgeHits(List<KnowledgeHit> semanticHits, List<KnowledgeHit> lexicalHits) {
        Map<String, KnowledgeHit> merged = new LinkedHashMap<>();
        appendHits(merged, semanticHits);
        appendHits(merged, lexicalHits);
        return new ArrayList<>(merged.values());
    }

    private List<KnowledgeHit> filterPersonalSensitiveHitsForCurrentUser(List<KnowledgeHit> hits,
                                                                         String currentUserNickname) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        if (currentUserNickname == null || currentUserNickname.isBlank()) {
            return Collections.emptyList();
        }
        Set<Integer> currentUserPersonalChunkNos = new LinkedHashSet<>();
        for (KnowledgeHit hit : hits) {
            if (personalSensitiveDataPolicy.hitContainsCurrentUser(hit, currentUserNickname)
                    && personalSensitiveDataPolicy.isPersonalSensitiveHit(hit)) {
                currentUserPersonalChunkNos.add(hit.getChunkNo());
            }
        }
        List<KnowledgeHit> filteredHits = new ArrayList<>();
        for (KnowledgeHit hit : hits) {
            if (hit == null) {
                continue;
            }
            if (!personalSensitiveDataPolicy.isPersonalSensitiveHit(hit)) {
                filteredHits.add(hit);
                continue;
            }
            if (personalSensitiveDataPolicy.hitContainsCurrentUser(hit, currentUserNickname)
                    || isAdjacentToCurrentUserPersonalChunk(hit, currentUserPersonalChunkNos)) {
                filteredHits.add(hit);
            }
        }
        return filteredHits;
    }

    private boolean isAdjacentToCurrentUserPersonalChunk(KnowledgeHit hit, Set<Integer> currentUserPersonalChunkNos) {
        if (hit.getChunkNo() == null || currentUserPersonalChunkNos == null || currentUserPersonalChunkNos.isEmpty()) {
            return false;
        }
        for (Integer chunkNo : currentUserPersonalChunkNos) {
            if (chunkNo != null && Math.abs(chunkNo - hit.getChunkNo()) <= 1) {
                return true;
            }
        }
        return false;
    }

    private void appendHits(Map<String, KnowledgeHit> merged, List<KnowledgeHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        for (KnowledgeHit hit : hits) {
            if (hit == null || hit.getContent() == null || hit.getContent().isBlank()) {
                continue;
            }
            merged.putIfAbsent(buildHitKey(hit), hit);
        }
    }

    private String buildHitKey(KnowledgeHit hit) {
        if (hit.getChunkId() != null) {
            return "chunk:" + hit.getChunkId();
        }
        if (hit.getVectorId() != null && !hit.getVectorId().isBlank()) {
            return "vector:" + hit.getVectorId();
        }
        return "document:" + hit.getDocumentId() + ":chunkNo:" + hit.getChunkNo()
                + ":content:" + hit.getContent().hashCode();
    }

    private List<KnowledgeHit> lexicalFallbackSearch(String question, KnowledgeSearchRequest searchRequest) {
        List<String> keywords = extractLexicalKeywords(question);
        if (keywords.isEmpty()) {
            return Collections.emptyList();
        }
        int topK = searchRequest.getTopK() == null || searchRequest.getTopK() <= 0 ? DEFAULT_TOP_K : searchRequest.getTopK();
        int candidateLimit = Math.min(Math.max(topK * LEXICAL_FALLBACK_MULTIPLIER, topK), 100);
        List<AiDocumentChunkDO> chunks = documentChunkMapper.selectLexicalCandidates(searchRequest.getTenantId(),
                searchRequest.getKnowledgeBaseId(), keywords, candidateLimit);
        if (chunks == null || chunks.isEmpty()) {
            log.info("RAG lexical fallback no hit, tenantId={}, departmentId={}, knowledgeBaseId={}, keywords={}",
                    searchRequest.getTenantId(), searchRequest.getDepartmentId(), searchRequest.getKnowledgeBaseId(),
                    keywords);
            return Collections.emptyList();
        }
        List<KnowledgeHit> allHits = chunks.stream()
                .map(chunk -> toLexicalHit(searchRequest, chunk, keywords))
                .filter(hit -> hit.getScore() != null && hit.getScore() > 0)
                .sorted(Comparator.comparing(KnowledgeHit::getScore, Comparator.reverseOrder())
                        .thenComparing(hit -> hit.getChunkNo() == null ? Integer.MAX_VALUE : hit.getChunkNo()))
                .toList();
        List<KnowledgeHit> hits = selectLexicalHitsWithIntentCoverage(question, allHits, topK);
        log.info("RAG lexical fallback hit, tenantId={}, departmentId={}, knowledgeBaseId={}, keywords={}, candidateCount={}, hitCount={}",
                searchRequest.getTenantId(), searchRequest.getDepartmentId(), searchRequest.getKnowledgeBaseId(),
                keywords, chunks.size(), hits.size());
        return hits;
    }

    private List<KnowledgeHit> selectLexicalHitsWithIntentCoverage(String question, List<KnowledgeHit> allHits,
                                                                   int topK) {
        if (allHits == null || allHits.isEmpty()) {
            return Collections.emptyList();
        }
        int lexicalLimit = Math.min(Math.max(topK * LEXICAL_RESULT_MULTIPLIER, topK), 20);
        Map<String, KnowledgeHit> selected = new LinkedHashMap<>();
        addIntentCoverageHits(question, allHits, selected);
        for (KnowledgeHit hit : allHits) {
            if (selected.size() >= lexicalLimit) {
                break;
            }
            selected.putIfAbsent(buildHitKey(hit), hit);
        }
        return new ArrayList<>(selected.values());
    }

    private void addIntentCoverageHits(String question, List<KnowledgeHit> allHits, Map<String, KnowledgeHit> selected) {
        if (question == null || question.isBlank()) {
            return;
        }
        for (List<String> keywordGroup : INTENT_COVERAGE_KEYWORD_GROUPS) {
            if (!containsAnyIgnoreCase(question, keywordGroup)) {
                continue;
            }
            allHits.stream()
                    .filter(hit -> hitMatchesAnyKeyword(hit, keywordGroup))
                    .findFirst()
                    .ifPresent(hit -> selected.putIfAbsent(buildHitKey(hit), hit));
        }
    }

    private boolean hitMatchesAnyKeyword(KnowledgeHit hit, List<String> keywords) {
        if (hit == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        return containsAnyIgnoreCase(buildHitSearchText(hit), keywords);
    }

    private String buildHitSearchText(KnowledgeHit hit) {
        if (hit == null) {
            return "";
        }
        return (hit.getDocumentTitle() == null ? "" : hit.getDocumentTitle()) + "\n"
                + (hit.getContent() == null ? "" : hit.getContent()) + "\n"
                + (hit.getMetadata() == null ? "" : hit.getMetadata().toString());
    }

    private boolean containsAnyIgnoreCase(String source, List<String> keywords) {
        if (source == null || source.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && containsIgnoreCase(source, keyword)) {
                return true;
            }
        }
        return false;
    }

    private KnowledgeHit toLexicalHit(KnowledgeSearchRequest searchRequest, AiDocumentChunkDO chunk,
                                      List<String> keywords) {
        Map<String, Object> metadata = parseMetadata(chunk.getMetadataJson());
        double score = calculateLexicalScore(chunk.getContent(), keywords);
        return KnowledgeHit.builder()
                .vectorId(chunk.getVectorId())
                .tenantId(searchRequest.getTenantId())
                .knowledgeBaseId(searchRequest.getKnowledgeBaseId())
                .documentId(chunk.getDocumentId())
                .chunkId(chunk.getId())
                .chunkNo(chunk.getChunkIndex())
                .documentTitle(extractDocumentTitle(metadata))
                .content(chunk.getContent())
                .score(score)
                .metadata(metadata)
                .build();
    }

    private List<String> extractLexicalKeywords(String question) {
        if (question == null || question.isBlank()) {
            return Collections.emptyList();
        }
        Set<String> keywords = new LinkedHashSet<>();
        String source = question.trim();
        for (String keyword : DOMAIN_KEYWORDS) {
            if (containsIgnoreCase(source, keyword)) {
                keywords.add(keyword);
            }
        }

        String normalized = source;
        for (String stopWord : QUERY_STOP_WORDS) {
            normalized = normalized.replace(stopWord, "");
        }
        String cjkText = normalized.replaceAll("[^\\p{IsHan}]", "");
        for (int i = 0; i + 2 <= cjkText.length(); i++) {
            String token = cjkText.substring(i, i + 2);
            if (!QUERY_STOP_WORDS.contains(token)) {
                keywords.add(token);
            }
            if (keywords.size() >= LEXICAL_KEYWORD_LIMIT) {
                break;
            }
        }

        Matcher matcher = ASCII_WORD_PATTERN.matcher(source);
        while (matcher.find() && keywords.size() < LEXICAL_KEYWORD_LIMIT) {
            keywords.add(matcher.group());
        }
        return keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .limit(LEXICAL_KEYWORD_LIMIT)
                .toList();
    }

    private double calculateLexicalScore(String content, List<String> keywords) {
        if (content == null || content.isBlank() || keywords == null || keywords.isEmpty()) {
            return 0D;
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        int matched = 0;
        for (String keyword : keywords) {
            if (lowerContent.contains(keyword.toLowerCase(Locale.ROOT))) {
                matched++;
            }
        }
        if (matched == 0) {
            return 0D;
        }
        return Math.min(0.95D, 0.2D + matched * 0.12D);
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(metadataJson, METADATA_TYPE));
        } catch (JsonProcessingException ex) {
            return Collections.emptyMap();
        }
    }

    private String extractDocumentTitle(Map<String, Object> metadata) {
        Object value = metadata.get("documentTitle");
        if (value == null) {
            value = metadata.get("title");
        }
        if (value == null) {
            value = metadata.get("filename");
        }
        return value == null ? null : value.toString();
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
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

    private String normalizeQuestion(String question) {
        if (question == null) {
            return "";
        }
        return question.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？；：“”‘’（）【】《》、]+", "");
    }

    private boolean isSelfIdentityQuestion(String normalizedQuestion) {
        return normalizedQuestion != null && SELF_IDENTITY_QUESTIONS.contains(normalizedQuestion);
    }

    private boolean isUserContextSensitiveQuestion(String normalizedQuestion) {
        if (normalizedQuestion == null || normalizedQuestion.isBlank()) {
            return false;
        }
        return normalizedQuestion.contains("我")
                || normalizedQuestion.contains("本人")
                || normalizedQuestion.contains("自己")
                || normalizedQuestion.contains("当前用户")
                || normalizedQuestion.contains("登录用户");
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

}
