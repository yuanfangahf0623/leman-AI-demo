package cn.iocoder.yudao.module.ai.service.rag;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.TwoHaoHrAttendanceStatReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.TwoHaoHrAttendanceStatRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatMessageDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatQuestionCacheDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentChunkDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatCitationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatConversationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatMessageMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatQuestionCacheMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentChunkMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiKnowledgeBaseMapper;
import cn.iocoder.yudao.module.ai.enums.ChatMessageRoleEnum;
import cn.iocoder.yudao.module.ai.enums.ChunkStatusEnum;
import cn.iocoder.yudao.module.ai.enums.KnowledgeVisibilityEnum;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeSearchRequest;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVectorStore;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelRequest;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelMessage;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.datasource.twohaohr.TwoHaoHrAttendanceStatService;
import cn.iocoder.yudao.module.ai.service.datasource.twohaohr.TwoHaoHrLeaveEmployeeListService;
import cn.iocoder.yudao.module.ai.service.datasource.twohaohr.TwoHaoHrLeaveEmployeeListService.LeaveEmployee;
import cn.iocoder.yudao.module.ai.service.datasource.twohaohr.TwoHaoHrLeaveEmployeeListService.LeaveEmployeeListResult;
import cn.iocoder.yudao.module.ai.service.embedding.AiEmbeddingService;
import cn.iocoder.yudao.module.ai.service.rag.config.AiRagEngineConfigService;
import cn.iocoder.yudao.module.ai.service.rag.fastgpt.FastGptRagClient;
import cn.iocoder.yudao.module.ai.service.rag.dify.DifyRagClient;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDifyConversationMapper;
import cn.iocoder.yudao.module.ai.service.rag.fastgpt.FastGptRagRequest;
import cn.iocoder.yudao.module.ai.service.rag.fastgpt.FastGptRagResult;
import cn.iocoder.yudao.module.ai.service.rag.retrieval.RetrievalModeEnum;
import cn.iocoder.yudao.module.ai.service.rag.retrieval.RetrievalPlan;
import cn.iocoder.yudao.module.ai.service.rag.retrieval.RetrievalPlanner;
import cn.iocoder.yudao.module.ai.service.rag.sensitive.PersonalSensitiveDataPolicy;
import cn.iocoder.yudao.module.ai.service.websearch.WebSearchResult;
import cn.iocoder.yudao.module.ai.service.websearch.WebSearchService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
    private static final Long ALL_KNOWLEDGE_BASE_ID = 0L;
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
    private static final int DEFAULT_WEB_SEARCH_TOP_K = 5;
    private static final int TABLE_INVENTORY_CHUNK_LIMIT = 500;
    private static final int TABLE_INVENTORY_CITATION_LIMIT = 10;
    private static final int CONVERSATION_CONTEXT_MESSAGE_LIMIT = 6;
    private static final int CONVERSATION_CONTEXT_MAX_CHARS = 1200;
    private static final int SHORT_FOLLOW_UP_MAX_LENGTH = 18;
    private static final int STRUCTURED_DOCUMENT_EXPANSION_MAX_CHUNKS = 500;
    private static final int STRUCTURED_DOCUMENT_EXPANSION_MAX_CHARS = 120_000;
    private static final int CLOTHING_SIZE_SEED_LIMIT = 50;
    private static final int CLOTHING_SIZE_CITATION_LIMIT = 4;
    private static final double WEB_SEARCH_SCORE = 0.5D;
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<RagChatCitation>> RAG_CITATION_LIST_TYPE = new TypeReference<>() {
    };
    private static final Pattern ASCII_WORD_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9.+#-]{1,}");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("表：\\s*([^\\s]+)");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("(20\\d{2})\\s*年\\s*(\\d{1,2})\\s*月");
    private static final Pattern MONTH_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*月");
    private static final Pattern CLOTHING_SIZE_PATTERN = Pattern.compile("(?i)(?:^|[^A-Z0-9])([2-9]XL|10XL|XL|XS|S|M|L)(?:[^A-Z0-9]|$)");
    private static final Pattern PERSONAL_ATTENDANCE_NAME_PATTERN = Pattern.compile(
            "(?:\\u5e2e\\u6211\\u67e5\\u4e00\\u4e0b|\\u5e2e\\u5fd9\\u67e5\\u4e00\\u4e0b|\\u67e5\\u8be2\\u4e00\\u4e0b|\\u67e5\\u770b\\u4e00\\u4e0b|\\u7edf\\u8ba1\\u4e00\\u4e0b|\\u6c47\\u603b\\u4e00\\u4e0b|\\u5e2e\\u6211\\u67e5|\\u5e2e\\u5fd9\\u67e5|\\u67e5\\u8be2|\\u67e5\\u770b|\\u7edf\\u8ba1|\\u6c47\\u603b|\\u67e5\\u4e0b|\\u770b\\u4e0b|\\u67e5|\\u770b|\\u8bf7)?\\s*"
                    + "([\\p{IsHan}]{2,4})(?:\\u7684)?(?:\\u8003\\u52e4|\\u6253\\u5361|\\u51fa\\u52e4|\\u8fdf\\u5230|\\u65e9\\u9000|\\u7f3a\\u5361|\\u8bf7\\u5047|\\u52a0\\u73ed|\\u5916\\u52e4)"
                    + "(?:\\u60c5\\u51b5|\\u8bb0\\u5f55|\\u6570\\u636e|\\u7edf\\u8ba1|\\u660e\\u7ec6)?");
    private static final Pattern CLOTHING_SIZE_COUNT_PATTERN = Pattern.compile("\\|\\s*(XS|S|M|L|XL|[2-9]XL|10XL)\\s*\\|\\s*\\*?\\*?(\\d+)\\*?\\*?\\s*\\|",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> ORGANIZATION_NAME_MARKERS = List.of(
            "\u90e8", "\u90e8\u95e8", "\u4e2d\u5fc3", "\u8f66\u95f4", "\u73ed\u7ec4", "\u5c0f\u7ec4", "\u7ec4",
            "\u516c\u53f8", "\u751f\u4ea7", "\u5236\u9020", "\u4e8b\u4e1a\u90e8", "\u79d1");
    private static final Set<String> PERSONAL_ATTENDANCE_NAME_STOP_WORDS = Set.of(
            "\u4e2a\u4eba", "\u672c\u4eba", "\u81ea\u5df1", "\u5458\u5de5", "\u5f53\u524d", "\u767b\u5f55",
            "\u7528\u6237", "\u540c\u4e8b", "\u4eba\u5458", "\u4e0a\u6708", "\u672c\u6708", "\u4eca\u5929",
            "\u4eca\u65e5", "\u6628\u5929", "\u6628\u65e5", "\u5f53\u6708", "\u8fd9\u4e2a", "\u4e0a\u4e2a",
            "\u6700\u8fd1");
    private static final Set<String> QUERY_STOP_WORDS = Set.of("目前", "现在", "现有", "当前", "请问", "哪些", "什么",
            "是什么", "有哪些", "有那些", "多少", "如何", "怎么", "可以", "一下", "如果", "情况下", "的情况下");
    private static final Set<String> SELF_IDENTITY_QUESTIONS = Set.of("我是谁", "请问我是谁", "我叫什么",
            "我叫什么名字", "我的名字是什么", "本人是谁", "当前用户是谁", "登录用户是谁");
    private static final List<String> DOMAIN_KEYWORDS = List.of("前端", "后端", "技术栈", "技术", "Vue", "Vite",
            "TypeScript", "Element", "Element Plus", "pnpm", "Java", "Spring", "MyBatis", "MySQL",
            "PostgreSQL", "pgvector", "Qdrant", "Redis", "Nacos", "MinIO", "RabbitMQ", "RAG", "Embedding",
            "MES", "数据表", "数据库表", "表结构", "表名", "字段", "字段名", "数据字典", "Table",
            "考勤", "全勤", "全勤奖", "绩效", "满绩效", "工资", "薪资", "奖金", "补贴", "岗位",
            "车间主任", "应发", "实发", "金额");
    private static final List<List<String>> INTENT_COVERAGE_KEYWORD_GROUPS = List.of(
            List.of("考勤", "全勤", "全勤奖", "出勤", "缺勤", "迟到", "早退"),
            List.of("绩效", "满绩效", "绩效工资", "绩效奖金"),
            List.of("工资", "薪资", "奖金", "补贴", "金额", "多少钱"),
            List.of("车间主任", "岗位", "职务")
    );
    private static final List<String> FOLLOW_UP_CUES = List.of("上面", "刚才", "继续", "这个", "那个",
            "它", "他", "她", "这里", "那里", "换成", "改成", "如果是", "那如果",
            "完整名单", "完整列表", "完整展示", "不要脱敏", "不脱敏", "姓名不要脱敏", "全部展示");
    private static final List<String> STANDALONE_INTENT_WORDS = List.of("什么", "哪些", "多少", "怎么",
            "如何", "为什么", "是否", "能不能", "需要", "可以", "排查", "统计", "查询", "翻译", "总结", "是谁");
    private static final List<String> STATISTICAL_QUESTION_KEYWORDS = List.of("统计", "汇总", "合计", "总数", "数量",
            "多少", "几条", "几项", "几个", "占比", "比例", "平均", "最大", "最小", "明细", "清单", "对应", "count",
            "total", "sum", "average", "avg", "max", "min");
    private static final List<String> STRUCTURED_DOCUMENT_KEYWORDS = List.of("Sheet:", "\t", "表：", "表:", "字段名称",
            "字段名", "工装尺寸", "数据表", "数据库表");
    private static final List<String> CLOTHING_SIZE_ORDER = List.of("XS", "S", "M", "L", "XL", "2XL", "3XL", "4XL",
            "5XL", "6XL", "7XL", "8XL", "9XL", "10XL");

    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final AiChatConversationMapper chatConversationMapper;
    private final AiChatMessageMapper chatMessageMapper;
    private final AiChatCitationMapper chatCitationMapper;
    private final AiChatQuestionCacheMapper chatQuestionCacheMapper;
    private final AiDocumentChunkMapper documentChunkMapper;
    private final AiEmbeddingService aiEmbeddingService;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final WebSearchService webSearchService;
    private final PromptBuilder promptBuilder;
    private final RetrievalPlanner retrievalPlanner;
    private final AiChatModelService aiChatModelService;
    private final FastGptRagClient fastGptRagClient;
    private final DifyRagClient difyRagClient;
    private final AiDifyConversationMapper difyConversationMapper;
    private final AiRagEngineConfigService ragEngineConfigService;
    private final TwoHaoHrAttendanceStatService twoHaoHrAttendanceStatService;
    private final TwoHaoHrLeaveEmployeeListService twoHaoHrLeaveEmployeeListService;
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

        List<AiKnowledgeBaseDO> knowledgeBases = validateKnowledgeAccessible(request.getKnowledgeBaseId(), tenantId,
                departmentId);
        AiKnowledgeBaseDO primaryKnowledgeBase = knowledgeBases.get(0);
        Long conversationKnowledgeBaseId = request.getKnowledgeBaseId();
        boolean allKnowledgeBase = isAllKnowledgeBase(conversationKnowledgeBaseId);
        boolean webSearchRequested = Boolean.TRUE.equals(request.getWebSearchEnabled());
        boolean webSearchEnabled = webSearchRequested && isWebSearchEnabled();
        String normalizedQuestion = normalizeQuestion(request.getQuestion());
        boolean rawPersonalSensitive = personalSensitiveDataPolicy.isSensitiveQuestion(request.getQuestion(), normalizedQuestion);
        validatePersonalSensitiveQuestionAccess(request.getQuestion(), normalizedQuestion, currentUserNickname, admin,
                rawPersonalSensitive);
        AiChatConversationDO conversation = getOrCreateConversation(request, tenantId, departmentId, userId);
        List<AiChatMessageDO> conversationContext = loadRecentConversationContext(conversation, tenantId);
        String effectiveQuestion = buildEffectiveQuestion(request.getQuestion(), conversationContext);
        boolean conversationContextApplied = !effectiveQuestion.equals(request.getQuestion());
        String normalizedEffectiveQuestion = normalizeQuestion(effectiveQuestion);
        boolean personalSensitive = rawPersonalSensitive || personalSensitiveDataPolicy.isSensitiveQuestion(effectiveQuestion,
                normalizedEffectiveQuestion);
        if (!rawPersonalSensitive && personalSensitive) {
            validatePersonalSensitiveQuestionAccess(effectiveQuestion, normalizedEffectiveQuestion,
                    currentUserNickname, admin, true);
        }
        AiChatMessageDO userMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.USER.getCode(), request.getQuestion(), null, 0L);
        RetrievalPlan retrievalPlan = retrievalPlanner.plan(effectiveQuestion);

        if (isSelfIdentityQuestion(normalizedQuestion)) {
            return saveCurrentUserIdentityAnswer(conversation, userMessage, tenantId, departmentId, userId,
                    currentUserNickname, startNanos);
        }

        RagChatResponse leaveEmployeeListResponse = tryAnswerTwoHaoHrLeaveEmployeeList(request, knowledgeBases,
                conversation, userMessage, tenantId, departmentId, userId, startNanos, effectiveQuestion);
        if (leaveEmployeeListResponse != null) {
            return leaveEmployeeListResponse;
        }

        RagChatResponse attendanceStatResponse = tryAnswerTwoHaoHrAttendanceStat(request, knowledgeBases, conversation,
                userMessage, tenantId, departmentId, userId, currentUserNickname, startNanos, effectiveQuestion,
                normalizedEffectiveQuestion);
        if (attendanceStatResponse != null) {
            return attendanceStatResponse;
        }

        if (AiRagEngineConfigService.ENGINE_DIFY.equals(ragEngineConfigService.getEngine())) {
            return chatWithDify(request, conversation, userMessage, knowledgeBases, tenantId, departmentId,
                    userId, startNanos);
        }

        if (isFastGptEngine()) {
            return chatWithHybridFastGpt(request, conversation, userMessage, conversationContext, knowledgeBases,
                    primaryKnowledgeBase, tenantId, departmentId, userId, currentUserNickname, admin, startNanos,
                    effectiveQuestion, conversationContextApplied, retrievalPlan, webSearchRequested,
                    webSearchEnabled, personalSensitive);
        }

        boolean clothingSizeCountQuestion = isClothingSizeCountQuestion(effectiveQuestion, normalizedEffectiveQuestion);
        if (clothingSizeCountQuestion) {
            RagChatResponse clothingSizeCountResponse = tryAnswerClothingSizeCount(request, knowledgeBases,
                    conversation, userMessage, tenantId, departmentId, userId, startNanos, effectiveQuestion);
            if (clothingSizeCountResponse != null) {
                return clothingSizeCountResponse;
            }
        }

        RagChatResponse clothingCorrectionResponse = tryAnswerClothingSizeCorrection(request.getQuestion(),
                conversationContext, conversation, userMessage, tenantId, departmentId, userId, startNanos);
        if (clothingCorrectionResponse != null) {
            return clothingCorrectionResponse;
        }

        boolean tableInventoryQuestion = isTableInventoryQuestion(effectiveQuestion, normalizedEffectiveQuestion);
        if (tableInventoryQuestion) {
            RagChatResponse tableInventoryResponse = tryAnswerTableInventory(request, knowledgeBases, conversation,
                    userMessage, tenantId, departmentId, userId, startNanos, effectiveQuestion);
            if (tableInventoryResponse != null) {
                return tableInventoryResponse;
            }
        }

        String questionHash = sha256Hex(QUESTION_CACHE_VERSION + ":" + normalizedEffectiveQuestion);
        boolean userContextSensitive = isUserContextSensitiveQuestion(normalizedEffectiveQuestion);
        boolean cacheableQuestion = !conversationContextApplied && !clothingSizeCountQuestion && !tableInventoryQuestion
                && retrievalPlan.isCacheable() && !webSearchRequested && !allKnowledgeBase
                && !userContextSensitive && !personalSensitive;
        if (cacheableQuestion) {
            AiChatQuestionCacheDO cachedAnswer = chatQuestionCacheMapper.selectLatest(tenantId, departmentId,
                    primaryKnowledgeBase.getId(), questionHash);
            if (cachedAnswer != null && cachedAnswer.getAnswer() != null && !cachedAnswer.getAnswer().isBlank()) {
                return saveCachedAnswer(conversation, userMessage, tenantId, departmentId, userId, primaryKnowledgeBase,
                        cachedAnswer, startNanos);
            }
        }

        List<KnowledgeHit> hits = searchKnowledge(effectiveQuestion, request, knowledgeBases, tenantId, departmentId);
        if (personalSensitive && !admin) {
            hits = filterPersonalSensitiveHitsForCurrentUser(hits, currentUserNickname);
        }
        if (webSearchEnabled && !personalSensitive) {
            hits = appendWebSearchHits(effectiveQuestion, hits, tenantId, conversationKnowledgeBaseId);
        } else if (webSearchRequested && personalSensitive) {
            log.info("RAG web search skipped for personal sensitive question, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}",
                    tenantId, departmentId, conversationKnowledgeBaseId, conversation.getId());
        }
        hits = expandHitsByRetrievalPlan(retrievalPlan, hits, tenantId);
        int hitCount = hits == null ? 0 : hits.size();
        PromptBuildResult prompt = promptBuilder.build(effectiveQuestion, hits, currentUserNickname, retrievalPlan);
        if (prompt.isNoContext()) {
            String debugInfo = buildLocalPlatformDebugInfo(request, knowledgeBases, retrievalPlan, conversation,
                    tenantId, departmentId, userId, effectiveQuestion, conversationContextApplied, webSearchRequested,
                    webSearchEnabled, personalSensitive, hitCount, prompt, null, 0L, 0, true,
                    elapsedMillis(startNanos));
            RagChatResponse response = saveFallbackAnswer(conversation, userMessage, tenantId, departmentId, userId,
                    appendDebugInfo(debugInfo, prompt.getDebugInfo()));
            log.info("RAG chat no effective context, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, hitCount={}, elapsedMs={}",
                    tenantId, departmentId, conversationKnowledgeBaseId, conversation.getId(), hitCount,
                    elapsedMillis(startNanos));
            return response;
        }

        long modelStartNanos = System.nanoTime();
        AiChatModelResponse modelResponse = aiChatModelService.chat(AiChatModelRequest.builder()
                .model(primaryKnowledgeBase.getChatModel())
                .systemPrompt(prompt.getSystemPrompt())
                .userPrompt(prompt.getUserPrompt())
                .build());
        long modelLatencyMs = elapsedMillis(modelStartNanos);
        String answer = modelResponse.getContent() == null || modelResponse.getContent().isBlank()
                ? FALLBACK_ANSWER : modelResponse.getContent();
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), answer, modelResponse, modelLatencyMs);
        List<RagChatCitation> citations = saveCitations(tenantId, departmentId, assistantMessage.getId(),
                conversationKnowledgeBaseId, prompt.getKnowledgeHits());
        if (cacheableQuestion) {
            saveQuestionCache(tenantId, departmentId, userId, primaryKnowledgeBase.getId(), request.getQuestion(),
                    normalizedQuestion, questionHash, answer, modelResponse, modelLatencyMs, citations);
        }
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);

        log.info("RAG chat success, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, hitCount={}, citationCount={}, elapsedMs={}",
                tenantId, departmentId, conversationKnowledgeBaseId, conversation.getId(), hitCount, citations.size(),
                elapsedMillis(startNanos));
        String debugInfo = buildLocalPlatformDebugInfo(request, knowledgeBases, retrievalPlan, conversation, tenantId,
                departmentId, userId, effectiveQuestion, conversationContextApplied, webSearchRequested,
                webSearchEnabled, personalSensitive, hitCount, prompt, modelResponse, modelLatencyMs, citations.size(),
                false, elapsedMillis(startNanos));
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(answer)
                .noContext(false)
                .debugInfo(appendDebugInfo(debugInfo, prompt.getDebugInfo()))
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

    private List<AiKnowledgeBaseDO> validateKnowledgeAccessible(Long knowledgeBaseId, Long tenantId, Long departmentId) {
        if (isAllKnowledgeBase(knowledgeBaseId)) {
            List<AiKnowledgeBaseDO> knowledgeBases = knowledgeBaseMapper.selectListByTenantId(tenantId).stream()
                    .filter(knowledgeBase -> isDepartmentAllowed(knowledgeBase, departmentId))
                    // The Dify entry point must keep its explicitly bound scope after history imports.
                    .filter(knowledgeBase -> !AiRagEngineConfigService.ENGINE_DIFY.equals(ragEngineConfigService.getEngine())
                            || difyRagClient.isBound(tenantId, knowledgeBase.getId()))
                    .toList();
            if (knowledgeBases.isEmpty()) {
                throw new ServiceException(RAG_KNOWLEDGE_NOT_EXISTS, "暂无可访问的知识库");
            }
            return knowledgeBases;
        }
        AiKnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectByIdAndTenantId(knowledgeBaseId, tenantId);
        if (knowledgeBase == null) {
            throw new ServiceException(RAG_KNOWLEDGE_NOT_EXISTS, "知识库不存在");
        }
        if (!isDepartmentAllowed(knowledgeBase, departmentId)) {
            throw new ServiceException(RAG_KNOWLEDGE_ACCESS_DENIED, "无权访问该知识库");
        }
        return List.of(knowledgeBase);
    }

    private boolean isAllKnowledgeBase(Long knowledgeBaseId) {
        return ALL_KNOWLEDGE_BASE_ID.equals(knowledgeBaseId);
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

    private List<AiChatMessageDO> loadRecentConversationContext(AiChatConversationDO conversation, Long tenantId) {
        if (conversation == null || conversation.getId() == null) {
            return Collections.emptyList();
        }
        List<AiChatMessageDO> messages = chatMessageMapper.selectListByConversationId(conversation.getId(), tenantId);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiChatMessageDO> effectiveMessages = messages.stream()
                .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                .filter(message -> ChatMessageRoleEnum.USER.getCode().equals(message.getRole())
                        || ChatMessageRoleEnum.ASSISTANT.getCode().equals(message.getRole()))
                .toList();
        int fromIndex = Math.max(0, effectiveMessages.size() - CONVERSATION_CONTEXT_MESSAGE_LIMIT);
        return new ArrayList<>(effectiveMessages.subList(fromIndex, effectiveMessages.size()));
    }

    private String buildEffectiveQuestion(String question, List<AiChatMessageDO> conversationContext) {
        String safeQuestion = question == null ? "" : question.trim();
        if (!shouldUseConversationContext(safeQuestion, conversationContext)) {
            return safeQuestion;
        }
        StringBuilder context = new StringBuilder();
        for (AiChatMessageDO message : conversationContext) {
            String role = ChatMessageRoleEnum.USER.getCode().equals(message.getRole()) ? "用户" : "助手";
            String content = truncateForContext(message.getContent(), 240);
            if (content.isBlank()) {
                continue;
            }
            context.append(role).append("：").append(content).append('\n');
            if (context.length() >= CONVERSATION_CONTEXT_MAX_CHARS) {
                break;
            }
        }
        if (context.length() == 0) {
            return safeQuestion;
        }
        return """
                多轮对话上下文：
                %s
                当前用户追问或补充：
                %s
                请结合上下文，将当前追问理解为完整问题后回答。
                """.formatted(truncateForContext(context.toString(), CONVERSATION_CONTEXT_MAX_CHARS), safeQuestion);
    }

    private boolean shouldUseConversationContext(String question, List<AiChatMessageDO> conversationContext) {
        if (question == null || question.isBlank() || conversationContext == null || conversationContext.isEmpty()) {
            return false;
        }
        String normalized = normalizeQuestion(question);
        if (containsAnyIgnoreCase(question, FOLLOW_UP_CUES)) {
            return true;
        }
        return normalized.length() <= SHORT_FOLLOW_UP_MAX_LENGTH
                && !containsAnyIgnoreCase(question, STANDALONE_INTENT_WORDS);
    }

    private String truncateForContext(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
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

    private boolean isFastGptEngine() {
        return ragEngineConfigService.isFastGptEngine();
    }

    private RagChatResponse chatWithDify(RagChatRequest request, AiChatConversationDO conversation,
                                        AiChatMessageDO userMessage, List<AiKnowledgeBaseDO> knowledgeBases,
                                        Long tenantId, Long departmentId, Long userId, long startNanos) {
        if (knowledgeBases.size() != 1 || !difyRagClient.isBound(tenantId, knowledgeBases.get(0).getId())) {
            throw new ServiceException(RAG_KNOWLEDGE_ACCESS_DENIED, "请选择已绑定 Dify 的知识库进行问答");
        }
        Long knowledgeBaseId = knowledgeBases.get(0).getId();
        String externalId = difyConversationMapper.find(tenantId, knowledgeBaseId, userId, conversation.getId());
        long modelStart = System.nanoTime();
        DifyRagClient.Result result = difyRagClient.chat(new DifyRagClient.Request(tenantId, knowledgeBaseId,
                userId, conversation.getId(), externalId, request.getQuestion()));
        difyConversationMapper.save(tenantId, knowledgeBaseId, userId, conversation.getId(), result.conversationId());
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), result.modelResponse().getContent(), result.modelResponse(),
                elapsedMillis(modelStart));
        List<RagChatCitation> citations = saveExternalCitations(tenantId, departmentId, assistantMessage.getId(),
                knowledgeBaseId, result.citations());
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);
        log.info("Dify chat completed, tenantId={}, knowledgeBaseId={}, conversationId={}, citationCount={}, elapsedMs={}",
                tenantId, knowledgeBaseId, conversation.getId(), citations.size(), elapsedMillis(startNanos));
        return RagChatResponse.builder().conversationId(conversation.getId()).userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId()).answer(result.modelResponse().getContent())
                .noContext(citations.isEmpty()).citations(citations).debugInfo("ragEngine=dify").build();
    }

    private RagChatResponse chatWithFastGpt(RagChatRequest request, AiChatConversationDO conversation,
                                            AiChatMessageDO userMessage,
                                            List<AiChatMessageDO> conversationContext,
                                            List<AiKnowledgeBaseDO> knowledgeBases,
                                            Long tenantId, Long departmentId, Long userId, long startNanos,
                                            String effectiveQuestion, boolean conversationContextApplied) {
        long modelStartNanos = System.nanoTime();
        FastGptRagResult fastGptResult = fastGptRagClient.chat(FastGptRagRequest.builder()
                .tenantId(tenantId)
                .departmentId(departmentId)
                .knowledgeBaseId(request.getKnowledgeBaseId())
                .conversationId(conversation.getId())
                .userId(userId)
                .question(effectiveQuestion)
                .messages(buildFastGptMessages(conversationContext, effectiveQuestion, conversationContextApplied))
                .build());
        long modelLatencyMs = elapsedMillis(modelStartNanos);
        AiChatModelResponse modelResponse = fastGptResult.getModelResponse();
        String answer = modelResponse == null || modelResponse.getContent() == null || modelResponse.getContent().isBlank()
                ? FALLBACK_ANSWER : modelResponse.getContent();
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), answer, modelResponse, modelLatencyMs);
        Long citationFallbackKnowledgeBaseId = resolveFastGptCitationKnowledgeBaseId(request.getKnowledgeBaseId(), knowledgeBases);
        List<RagChatCitation> citations = saveExternalCitations(tenantId, departmentId, assistantMessage.getId(),
                citationFallbackKnowledgeBaseId, fastGptResult.getCitations());
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);
        log.info("RAG chat delegated to FastGPT, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, citationCount={}, elapsedMs={}",
                tenantId, departmentId, request.getKnowledgeBaseId(), conversation.getId(), citations.size(),
                elapsedMillis(startNanos));
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(answer)
                .noContext(FALLBACK_ANSWER.equals(answer) && citations.isEmpty())
                .debugInfo(buildFastGptDebugInfo(request, knowledgeBases, tenantId, departmentId, userId,
                        conversation.getId(), effectiveQuestion, conversationContextApplied, modelResponse,
                        modelLatencyMs, citations.size(), elapsedMillis(startNanos), fastGptResult.getDebugInfo()))
                .citations(citations)
                .build();
    }

    private RagChatResponse chatWithHybridFastGpt(RagChatRequest request, AiChatConversationDO conversation,
                                                  AiChatMessageDO userMessage,
                                                  List<AiChatMessageDO> conversationContext,
                                                  List<AiKnowledgeBaseDO> knowledgeBases,
                                                  AiKnowledgeBaseDO primaryKnowledgeBase,
                                                  Long tenantId, Long departmentId, Long userId,
                                                  String currentUserNickname, boolean admin, long startNanos,
                                                  String effectiveQuestion, boolean conversationContextApplied,
                                                  RetrievalPlan retrievalPlan, boolean webSearchRequested,
                                                  boolean webSearchEnabled, boolean personalSensitive) {
        FastGptCallOutcome fastGptOutcome = callFastGptForHybrid(request, conversation, conversationContext,
                tenantId, departmentId, userId, effectiveQuestion, conversationContextApplied, personalSensitive);

        List<KnowledgeHit> hits = Collections.emptyList();
        PromptBuildResult prompt;
        String localSearchError = null;
        try {
            hits = searchKnowledge(effectiveQuestion, request, knowledgeBases, tenantId, departmentId);
            if (personalSensitive && !admin) {
                hits = filterPersonalSensitiveHitsForCurrentUser(hits, currentUserNickname);
            }
            if (webSearchEnabled && !personalSensitive) {
                hits = appendWebSearchHits(effectiveQuestion, hits, tenantId, request.getKnowledgeBaseId());
            } else if (webSearchRequested && personalSensitive) {
                log.info("RAG web search skipped for personal sensitive question, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}",
                        tenantId, departmentId, request.getKnowledgeBaseId(), conversation.getId());
            }
            hits = expandHitsByRetrievalPlan(retrievalPlan, hits, tenantId);
            prompt = promptBuilder.build(effectiveQuestion, hits, currentUserNickname, retrievalPlan);
        } catch (RuntimeException ex) {
            localSearchError = ex.getClass().getSimpleName();
            log.warn("RAG hybrid local search failed, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, errorType={}, error={}",
                    tenantId, departmentId, request.getKnowledgeBaseId(), conversation.getId(),
                    ex.getClass().getSimpleName(), ex.getMessage());
            prompt = PromptBuildResult.builder()
                    .status(PromptBuildResult.STATUS_NO_CONTEXT)
                    .knowledgeHits(Collections.emptyList())
                    .estimatedContextTokens(0)
                    .debugInfo("Hybrid local search failed: " + localSearchError)
                    .build();
        }
        int hitCount = hits == null ? 0 : hits.size();
        List<RagChatCitation> fastGptCitations = getFastGptCitations(fastGptOutcome);
        boolean fastGptHasAnswer = hasUsefulFastGptAnswer(fastGptOutcome);
        if (prompt.isNoContext()) {
            if (fastGptHasAnswer || !fastGptCitations.isEmpty()) {
                return saveFastGptOnlyHybridAnswer(request, conversation, userMessage, knowledgeBases, tenantId,
                        departmentId, userId, startNanos, effectiveQuestion, conversationContextApplied,
                        retrievalPlan, webSearchRequested, webSearchEnabled, personalSensitive, hitCount, prompt,
                        fastGptOutcome, localSearchError);
            }
            String debugInfo = buildHybridFastGptDebugInfo(request, knowledgeBases, retrievalPlan, conversation,
                    tenantId, departmentId, userId, effectiveQuestion, conversationContextApplied, webSearchRequested,
                    webSearchEnabled, personalSensitive, hitCount, prompt, null, 0L, 0, true,
                    elapsedMillis(startNanos), fastGptOutcome, "fallback", localSearchError);
            RagChatResponse response = saveFallbackAnswer(conversation, userMessage, tenantId, departmentId, userId,
                    appendDebugInfo(debugInfo, prompt.getDebugInfo()));
            log.info("RAG hybrid no effective context, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, localHitCount={}, fastGptCitationCount={}, elapsedMs={}",
                    tenantId, departmentId, request.getKnowledgeBaseId(), conversation.getId(), hitCount,
                    fastGptCitations.size(), elapsedMillis(startNanos));
            return response;
        }

        String fastGptAnswer = resolveFastGptAnswer(fastGptOutcome);
        long modelStartNanos = System.nanoTime();
        AiChatModelResponse modelResponse = aiChatModelService.chat(AiChatModelRequest.builder()
                .model(primaryKnowledgeBase.getChatModel())
                .systemPrompt(buildHybridSystemPrompt())
                .userPrompt(buildHybridUserPrompt(effectiveQuestion, prompt, fastGptAnswer, fastGptCitations,
                        fastGptOutcome))
                .metadata(Map.of("ragEngine", "fastgpt+local", "finalAnswerSource", "local-model-hybrid"))
                .build());
        long modelLatencyMs = elapsedMillis(modelStartNanos);
        String answer = modelResponse.getContent() == null || modelResponse.getContent().isBlank()
                ? FALLBACK_ANSWER : modelResponse.getContent();
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), answer, modelResponse, modelLatencyMs);
        List<RagChatCitation> citations = new ArrayList<>();
        citations.addAll(saveCitations(tenantId, departmentId, assistantMessage.getId(), request.getKnowledgeBaseId(),
                prompt.getKnowledgeHits(), 0));
        Long fastGptCitationFallbackKnowledgeBaseId = resolveFastGptCitationKnowledgeBaseId(request.getKnowledgeBaseId(),
                knowledgeBases);
        citations.addAll(saveExternalCitations(tenantId, departmentId, assistantMessage.getId(),
                fastGptCitationFallbackKnowledgeBaseId, fastGptCitations, citations.size()));
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);

        log.info("RAG hybrid chat success, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, localHitCount={}, fastGptCitationCount={}, citationCount={}, elapsedMs={}",
                tenantId, departmentId, request.getKnowledgeBaseId(), conversation.getId(), hitCount,
                fastGptCitations.size(), citations.size(), elapsedMillis(startNanos));
        String debugInfo = buildHybridFastGptDebugInfo(request, knowledgeBases, retrievalPlan, conversation, tenantId,
                departmentId, userId, effectiveQuestion, conversationContextApplied, webSearchRequested,
                webSearchEnabled, personalSensitive, hitCount, prompt, modelResponse, modelLatencyMs, citations.size(),
                false, elapsedMillis(startNanos), fastGptOutcome, "local-model-hybrid", localSearchError);
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(answer)
                .noContext(false)
                .debugInfo(appendDebugInfo(debugInfo, prompt.getDebugInfo()))
                .citations(citations)
                .build();
    }

    private FastGptCallOutcome callFastGptForHybrid(RagChatRequest request, AiChatConversationDO conversation,
                                                    List<AiChatMessageDO> conversationContext,
                                                    Long tenantId, Long departmentId, Long userId,
                                                    String effectiveQuestion, boolean conversationContextApplied,
                                                    boolean personalSensitive) {
        if (personalSensitive) {
            return new FastGptCallOutcome(null, null, 0L, true,
                    "skipped for personal sensitive question");
        }
        long modelStartNanos = System.nanoTime();
        try {
            FastGptRagResult fastGptResult = fastGptRagClient.chat(FastGptRagRequest.builder()
                    .tenantId(tenantId)
                    .departmentId(departmentId)
                    .knowledgeBaseId(request.getKnowledgeBaseId())
                    .conversationId(conversation.getId())
                    .userId(userId)
                    .question(effectiveQuestion)
                    .messages(buildFastGptMessages(conversationContext, effectiveQuestion, conversationContextApplied))
                    .build());
            return new FastGptCallOutcome(fastGptResult, fastGptResult.getModelResponse(),
                    elapsedMillis(modelStartNanos), false, null);
        } catch (ServiceException ex) {
            log.warn("RAG hybrid FastGPT call failed, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, code={}, error={}",
                    tenantId, departmentId, request.getKnowledgeBaseId(), conversation.getId(), ex.getCode(),
                    ex.getMessage());
            return new FastGptCallOutcome(null, null, elapsedMillis(modelStartNanos), false,
                    ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("RAG hybrid FastGPT call exception, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, errorType={}, error={}",
                    tenantId, departmentId, request.getKnowledgeBaseId(), conversation.getId(),
                    ex.getClass().getSimpleName(), ex.getMessage());
            return new FastGptCallOutcome(null, null, elapsedMillis(modelStartNanos), false,
                    ex.getClass().getSimpleName());
        }
    }

    private RagChatResponse saveFastGptOnlyHybridAnswer(RagChatRequest request, AiChatConversationDO conversation,
                                                        AiChatMessageDO userMessage,
                                                        List<AiKnowledgeBaseDO> knowledgeBases, Long tenantId,
                                                        Long departmentId, Long userId, long startNanos,
                                                        String effectiveQuestion,
                                                        boolean conversationContextApplied,
                                                        RetrievalPlan retrievalPlan, boolean webSearchRequested,
                                                        boolean webSearchEnabled, boolean personalSensitive,
                                                        int hitCount, PromptBuildResult prompt,
                                                        FastGptCallOutcome fastGptOutcome,
                                                        String localSearchError) {
        AiChatModelResponse modelResponse = fastGptOutcome.modelResponse();
        String answer = resolveFastGptAnswer(fastGptOutcome);
        if (!hasText(answer)) {
            answer = FALLBACK_ANSWER;
        }
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), answer, modelResponse, fastGptOutcome.latencyMs());
        Long citationFallbackKnowledgeBaseId = resolveFastGptCitationKnowledgeBaseId(request.getKnowledgeBaseId(),
                knowledgeBases);
        List<RagChatCitation> citations = saveExternalCitations(tenantId, departmentId, assistantMessage.getId(),
                citationFallbackKnowledgeBaseId, getFastGptCitations(fastGptOutcome), 0);
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);
        String debugInfo = buildHybridFastGptDebugInfo(request, knowledgeBases, retrievalPlan, conversation, tenantId,
                departmentId, userId, effectiveQuestion, conversationContextApplied, webSearchRequested,
                webSearchEnabled, personalSensitive, hitCount, prompt, modelResponse, fastGptOutcome.latencyMs(),
                citations.size(), FALLBACK_ANSWER.equals(answer) && citations.isEmpty(), elapsedMillis(startNanos),
                fastGptOutcome, "fastgpt-only", localSearchError);
        log.info("RAG hybrid answered by FastGPT only, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, citationCount={}, elapsedMs={}",
                tenantId, departmentId, request.getKnowledgeBaseId(), conversation.getId(), citations.size(),
                elapsedMillis(startNanos));
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(answer)
                .noContext(FALLBACK_ANSWER.equals(answer) && citations.isEmpty())
                .debugInfo(appendDebugInfo(debugInfo, prompt.getDebugInfo()))
                .citations(citations)
                .build();
    }

    private String buildHybridSystemPrompt() {
        return """
                你是企业内部知识库助手。
                你会同时收到两类资料：
                1. 本地知识库片段：已经过租户、部门和知识库权限过滤，回答企业内部、个人敏感、业务数据问题时优先使用。
                2. FastGPT 引擎结果：作为外部 RAG 引擎的补充结果。
                回答规则：
                - 优先基于本地知识库片段回答；FastGPT 结果只能补充，不得覆盖本地证据。
                - 如果资料不足，回答“根据当前知识库资料无法确认”。
                - 不要编造不存在的制度、数据、流程或结论。
                - 先直接回答结论，再给出依据；如果有来源，请列出来源文档。
                """;
    }

    private String buildHybridUserPrompt(String effectiveQuestion, PromptBuildResult prompt, String fastGptAnswer,
                                         List<RagChatCitation> fastGptCitations,
                                         FastGptCallOutcome fastGptOutcome) {
        return """
                用户问题：
                %s

                FastGPT 引擎结果：
                %s

                FastGPT 引用摘要：
                %s

                本地知识库 Prompt：
                %s

                编排提示：
                - 本地知识库命中时，必须优先采用本地知识库证据。
                - FastGPT 调用失败或未返回内容时，忽略 FastGPT 结果。
                - 如果本地知识库与 FastGPT 结果冲突，以本地知识库为准。
                - 当前 FastGPT 状态：%s。
                """.formatted(effectiveQuestion, hasText(fastGptAnswer) ? fastGptAnswer : "无",
                formatExternalCitationSummary(fastGptCitations),
                prompt == null || prompt.getUserPrompt() == null ? "" : prompt.getUserPrompt(),
                fastGptOutcomeStatus(fastGptOutcome));
    }

    private String formatExternalCitationSummary(List<RagChatCitation> citations) {
        if (citations == null || citations.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(citations.size(), 10);
        for (int i = 0; i < limit; i++) {
            RagChatCitation citation = citations.get(i);
            builder.append(i + 1).append(". knowledgeBase=")
                    .append(hasText(citation.getKnowledgeBaseName()) ? citation.getKnowledgeBaseName() : "FastGPT")
                    .append(", title=").append(citation.getDocumentTitle())
                    .append(", score=").append(citation.getScore())
                    .append(", quote=")
                    .append(truncateForContext(citation.getQuoteText(), 500))
                    .append('\n');
        }
        if (citations.size() > limit) {
            builder.append("其余 ").append(citations.size() - limit).append(" 条 FastGPT 引用未展开。");
        }
        return builder.toString();
    }

    private String buildHybridFastGptDebugInfo(RagChatRequest request, List<AiKnowledgeBaseDO> knowledgeBases,
                                               RetrievalPlan retrievalPlan, AiChatConversationDO conversation,
                                               Long tenantId, Long departmentId, Long userId,
                                               String effectiveQuestion, boolean conversationContextApplied,
                                               boolean webSearchRequested, boolean webSearchEnabled,
                                               boolean personalSensitive, int hitCount, PromptBuildResult prompt,
                                               AiChatModelResponse modelResponse, long modelLatencyMs,
                                               int citationCount, boolean noContext, long elapsedMs,
                                               FastGptCallOutcome fastGptOutcome, String finalAnswerSource,
                                               String localSearchError) {
        StringBuilder debug = new StringBuilder();
        debug.append("## Hybrid RAG execution trace\n");
        debug.append("- ragEngine=fastgpt+local\n");
        debug.append("- finalAnswerSource=").append(finalAnswerSource)
                .append(", localHitCount=").append(hitCount)
                .append(", persistedCitationCount=").append(citationCount)
                .append(", noContext=").append(noContext)
                .append(", totalElapsedMs=").append(elapsedMs).append('\n');
        debug.append("- fastGptStatus=").append(fastGptOutcomeStatus(fastGptOutcome))
                .append(", fastGptLatencyMs=").append(fastGptOutcome == null ? 0L : fastGptOutcome.latencyMs())
                .append(", fastGptCitationCount=").append(getFastGptCitations(fastGptOutcome).size())
                .append('\n');
        if (hasText(localSearchError)) {
            debug.append("- localSearchError=").append(localSearchError).append('\n');
        }
        String localDebugInfo = buildLocalPlatformDebugInfo(request, knowledgeBases, retrievalPlan, conversation,
                tenantId, departmentId, userId, effectiveQuestion, conversationContextApplied, webSearchRequested,
                webSearchEnabled, personalSensitive, hitCount, prompt, modelResponse, modelLatencyMs, citationCount,
                noContext, elapsedMs);
        String fastGptDebugInfo = fastGptOutcome == null || fastGptOutcome.result() == null
                ? "" : fastGptOutcome.result().getDebugInfo();
        return appendDebugInfo(debug.toString(), appendDebugInfo(localDebugInfo, fastGptDebugInfo));
    }

    private String resolveFastGptAnswer(FastGptCallOutcome fastGptOutcome) {
        if (fastGptOutcome == null || fastGptOutcome.modelResponse() == null) {
            return null;
        }
        String content = fastGptOutcome.modelResponse().getContent();
        return hasText(content) ? content : null;
    }

    private boolean hasUsefulFastGptAnswer(FastGptCallOutcome fastGptOutcome) {
        String answer = resolveFastGptAnswer(fastGptOutcome);
        return hasText(answer) && !FALLBACK_ANSWER.equals(answer);
    }

    private List<RagChatCitation> getFastGptCitations(FastGptCallOutcome fastGptOutcome) {
        if (fastGptOutcome == null || fastGptOutcome.result() == null
                || fastGptOutcome.result().getCitations() == null) {
            return Collections.emptyList();
        }
        return fastGptOutcome.result().getCitations();
    }

    private String fastGptOutcomeStatus(FastGptCallOutcome fastGptOutcome) {
        if (fastGptOutcome == null) {
            return "not-called";
        }
        if (fastGptOutcome.skipped()) {
            return "skipped:" + fastGptOutcome.errorMessage();
        }
        if (hasText(fastGptOutcome.errorMessage())) {
            return "failed:" + fastGptOutcome.errorMessage();
        }
        return fastGptOutcome.result() == null ? "empty" : "success";
    }

    private List<AiChatModelMessage> buildFastGptMessages(List<AiChatMessageDO> conversationContext,
                                                          String effectiveQuestion,
                                                          boolean conversationContextApplied) {
        List<AiChatModelMessage> messages = new ArrayList<>();
        if (!conversationContextApplied && conversationContext != null) {
            for (AiChatMessageDO message : conversationContext) {
                if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                    continue;
                }
                if (!ChatMessageRoleEnum.USER.getCode().equals(message.getRole())
                        && !ChatMessageRoleEnum.ASSISTANT.getCode().equals(message.getRole())) {
                    continue;
                }
                messages.add(AiChatModelMessage.builder()
                        .role(message.getRole())
                        .content(truncateForContext(message.getContent(), CONVERSATION_CONTEXT_MAX_CHARS))
                        .build());
            }
        }
        messages.add(AiChatModelMessage.builder()
                .role(ChatMessageRoleEnum.USER.getCode())
                .content(effectiveQuestion)
                .build());
        return messages;
    }

    private Long resolveFastGptCitationKnowledgeBaseId(Long requestedKnowledgeBaseId,
                                                       List<AiKnowledgeBaseDO> knowledgeBases) {
        if (!isAllKnowledgeBase(requestedKnowledgeBaseId)) {
            return requestedKnowledgeBaseId;
        }
        // 全部知识库模式下不能随意选一个本地知识库作为引用来源，否则会误显示为 n8n 等本地知识库。
        return requestedKnowledgeBaseId;
    }

    private String buildFastGptDebugInfo(RagChatRequest request, List<AiKnowledgeBaseDO> knowledgeBases,
                                         Long tenantId, Long departmentId, Long userId, Long conversationId,
                                         String effectiveQuestion, boolean conversationContextApplied,
                                         AiChatModelResponse modelResponse, long modelLatencyMs, int citationCount,
                                         long elapsedMs, String fastGptDebugInfo) {
        StringBuilder debug = new StringBuilder();
        debug.append("## RAG 平台层执行轨迹\n");
        debug.append("> 说明：以下为本系统可审计的编排、权限、会话、外部引擎和引用解析过程，不包含模型内部原始思考过程。\n\n");
        debug.append("### 1. 本地编排\n");
        debug.append("- ragEngine=fastgpt\n");
        debug.append("- tenantId=").append(tenantId)
                .append(", departmentId=").append(departmentId)
                .append(", userId=").append(userId)
                .append(", conversationId=").append(conversationId).append('\n');
        debug.append("- requestedKnowledgeBaseId=").append(request.getKnowledgeBaseId())
                .append(", accessibleKnowledgeBaseCount=").append(knowledgeBases == null ? 0 : knowledgeBases.size())
                .append('\n');
        debug.append("- accessibleKnowledgeBases=").append(formatKnowledgeBaseScope(knowledgeBases)).append('\n');
        debug.append("- conversationContextApplied=").append(conversationContextApplied)
                .append(", effectiveQuestionChars=").append(effectiveQuestion == null ? 0 : effectiveQuestion.length())
                .append('\n');
        debug.append("- localPolicy=本地只负责登录用户、租户、部门、知识库访问校验和问答日志落库；检索和生成委托给 FastGPT。\n\n");
        debug.append("### 2. 模型与引用结果\n");
        debug.append("- model=").append(modelResponse == null ? "" : modelResponse.getModel())
                .append(", modelLatencyMs=").append(modelLatencyMs)
                .append(", totalElapsedMs=").append(elapsedMs).append('\n');
        debug.append("- promptTokens=").append(modelResponse == null ? null : modelResponse.getPromptTokens())
                .append(", completionTokens=").append(modelResponse == null ? null : modelResponse.getCompletionTokens())
                .append(", totalTokens=").append(modelResponse == null ? null : modelResponse.getTotalTokens())
                .append('\n');
        debug.append("- persistedCitationCount=").append(citationCount).append('\n');
        if (fastGptDebugInfo == null || fastGptDebugInfo.isBlank()) {
            return debug.toString();
        }
        return appendDebugInfo(debug.toString(), fastGptDebugInfo);
    }

    private String buildLocalPlatformDebugInfo(RagChatRequest request, List<AiKnowledgeBaseDO> knowledgeBases,
                                               RetrievalPlan retrievalPlan, AiChatConversationDO conversation,
                                               Long tenantId, Long departmentId, Long userId,
                                               String effectiveQuestion, boolean conversationContextApplied,
                                               boolean webSearchRequested, boolean webSearchEnabled,
                                               boolean personalSensitive, int hitCount, PromptBuildResult prompt,
                                               AiChatModelResponse modelResponse, long modelLatencyMs,
                                               int citationCount, boolean noContext, long elapsedMs) {
        StringBuilder debug = new StringBuilder();
        debug.append("## RAG 平台层执行轨迹\n");
        debug.append("> 说明：以下为本系统可审计的检索、扩展、Prompt、模型和引用过程，不包含模型内部原始思考过程。\n\n");
        debug.append("### 1. 请求与权限\n");
        debug.append("- ragEngine=local\n");
        debug.append("- tenantId=").append(tenantId)
                .append(", departmentId=").append(departmentId)
                .append(", userId=").append(userId)
                .append(", conversationId=").append(conversation == null ? null : conversation.getId()).append('\n');
        debug.append("- requestedKnowledgeBaseId=").append(request.getKnowledgeBaseId())
                .append(", accessibleKnowledgeBaseCount=").append(knowledgeBases == null ? 0 : knowledgeBases.size())
                .append('\n');
        debug.append("- accessibleKnowledgeBases=").append(formatKnowledgeBaseScope(knowledgeBases)).append('\n');
        debug.append("- conversationContextApplied=").append(conversationContextApplied)
                .append(", effectiveQuestionChars=").append(effectiveQuestion == null ? 0 : effectiveQuestion.length())
                .append('\n');
        debug.append("- personalSensitive=").append(personalSensitive)
                .append(", webSearchRequested=").append(webSearchRequested)
                .append(", webSearchEnabled=").append(webSearchEnabled).append("\n\n");

        debug.append("### 2. 检索计划与召回\n");
        if (retrievalPlan == null) {
            debug.append("- retrievalPlan=null\n");
        } else {
            debug.append("- mode=").append(retrievalPlan.getMode())
                    .append(", questionType=").append(retrievalPlan.getQuestionType())
                    .append(", fullDocumentRequired=").append(retrievalPlan.isFullDocumentRequired())
                    .append(", structuredDataRequired=").append(retrievalPlan.isStructuredDataRequired())
                    .append(", strictEvidenceRequired=").append(retrievalPlan.isStrictEvidenceRequired()).append('\n');
            debug.append("- reason=").append(retrievalPlan.getReason()).append('\n');
        }
        debug.append("- hitCountAfterMergeAndExpansion=").append(hitCount)
                .append(", promptContextHitCount=")
                .append(prompt == null || prompt.getKnowledgeHits() == null ? 0 : prompt.getKnowledgeHits().size())
                .append(", estimatedContextTokens=")
                .append(prompt == null ? null : prompt.getEstimatedContextTokens()).append('\n');
        debug.append("- noContext=").append(noContext).append('\n');
        appendHitSummary(debug, prompt == null ? Collections.emptyList() : prompt.getKnowledgeHits());

        debug.append("\n### 3. 模型与引用结果\n");
        debug.append("- modelCalled=").append(modelResponse != null)
                .append(", model=").append(modelResponse == null ? "" : modelResponse.getModel())
                .append(", modelLatencyMs=").append(modelLatencyMs)
                .append(", totalElapsedMs=").append(elapsedMs).append('\n');
        debug.append("- promptTokens=").append(modelResponse == null ? null : modelResponse.getPromptTokens())
                .append(", completionTokens=").append(modelResponse == null ? null : modelResponse.getCompletionTokens())
                .append(", totalTokens=").append(modelResponse == null ? null : modelResponse.getTotalTokens())
                .append('\n');
        debug.append("- persistedCitationCount=").append(citationCount).append('\n');
        return debug.toString();
    }

    private void appendHitSummary(StringBuilder debug, List<KnowledgeHit> hits) {
        debug.append("- contextHitSummary：");
        if (hits == null || hits.isEmpty()) {
            debug.append("无\n");
            return;
        }
        debug.append('\n');
        int limit = Math.min(hits.size(), 10);
        for (int i = 0; i < limit; i++) {
            KnowledgeHit hit = hits.get(i);
            debug.append("  - Hit ").append(i + 1)
                    .append(": kbId=").append(hit.getKnowledgeBaseId())
                    .append(", documentId=").append(hit.getDocumentId())
                    .append(", chunkId=").append(hit.getChunkId())
                    .append(", chunkNo=").append(hit.getChunkNo())
                    .append(", score=").append(hit.getScore())
                    .append(", title=").append(hit.getDocumentTitle()).append('\n');
        }
        if (hits.size() > limit) {
            debug.append("  - 其余 ").append(hits.size() - limit).append(" 条未展开展示。\n");
        }
    }

    private String formatKnowledgeBaseScope(List<AiKnowledgeBaseDO> knowledgeBases) {
        if (knowledgeBases == null || knowledgeBases.isEmpty()) {
            return "[]";
        }
        List<String> names = knowledgeBases.stream()
                .map(knowledgeBase -> knowledgeBase.getId() + ":" + safeDebugText(knowledgeBase.getName()))
                .toList();
        return names.toString();
    }

    private String safeDebugText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return truncateForContext(value, 80);
    }

    private String appendDebugInfo(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + "\n\n" + second;
    }

    private RagChatResponse tryAnswerClothingSizeCount(RagChatRequest request, List<AiKnowledgeBaseDO> knowledgeBases,
                                                       AiChatConversationDO conversation, AiChatMessageDO userMessage,
                                                       Long tenantId, Long departmentId, Long userId,
                                                       long startNanos, String effectiveQuestion) {
        ClothingSizeCountResult result = collectClothingSizeCounts(tenantId, knowledgeBases);
        if (result.totalCount() <= 0) {
            return null;
        }
        String answer = buildClothingSizeCountAnswer(result);
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), answer, null, 0L);
        List<RagChatCitation> citations = saveCitations(tenantId, departmentId, assistantMessage.getId(),
                request.getKnowledgeBaseId(), buildClothingSizeCitationHits(result));
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);
        log.info("RAG clothing size count answered directly, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, documentCount={}, totalCount={}, citationCount={}, elapsedMs={}",
                tenantId, departmentId, request.getKnowledgeBaseId(), conversation.getId(),
                result.documentCount(), result.totalCount(), citations.size(), elapsedMillis(startNanos));
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(answer)
                .noContext(false)
                .debugInfo(buildClothingSizeCountDebugInfo(effectiveQuestion, result))
                .citations(citations)
                .build();
    }

    private ClothingSizeCountResult collectClothingSizeCounts(Long tenantId, List<AiKnowledgeBaseDO> knowledgeBases) {
        Map<String, Integer> sizeCounts = initSizeCountMap();
        Map<String, KnowledgeHit> citationHits = new LinkedHashMap<>();
        Set<Long> handledDocumentIds = new LinkedHashSet<>();
        int total = 0;
        for (AiKnowledgeBaseDO knowledgeBase : knowledgeBases) {
            List<AiDocumentChunkDO> seedChunks = documentChunkMapper.selectClothingSizeSeedCandidates(tenantId,
                    knowledgeBase.getId(), CLOTHING_SIZE_SEED_LIMIT);
            if (seedChunks == null || seedChunks.isEmpty()) {
                continue;
            }
            for (AiDocumentChunkDO seedChunk : seedChunks) {
                if (!handledDocumentIds.add(seedChunk.getDocumentId())) {
                    continue;
                }
                List<AiDocumentChunkDO> documentChunks = documentChunkMapper.selectListByDocumentIdAndTenantId(
                        seedChunk.getDocumentId(), seedChunk.getKnowledgeBaseId(), tenantId);
                String content = mergeChunkContents(documentChunks);
                ClothingSizeParseResult parseResult = parseClothingSizeRows(content);
                if (parseResult.totalCount() <= 0) {
                    continue;
                }
                total += parseResult.totalCount();
                parseResult.sizeCounts().forEach((size, count) ->
                        sizeCounts.put(size, sizeCounts.getOrDefault(size, 0) + count));
                appendClothingSizeCitationHits(citationHits, knowledgeBase, documentChunks);
            }
        }
        sizeCounts.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0);
        return new ClothingSizeCountResult(sizeCounts, total, handledDocumentIds.size(),
                new ArrayList<>(citationHits.values()));
    }

    private Map<String, Integer> initSizeCountMap() {
        Map<String, Integer> sizeCounts = new LinkedHashMap<>();
        for (String size : CLOTHING_SIZE_ORDER) {
            sizeCounts.put(size, 0);
        }
        return sizeCounts;
    }

    private String mergeChunkContents(List<AiDocumentChunkDO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder merged = new StringBuilder();
        chunks.stream()
                .sorted(Comparator.comparing(AiDocumentChunkDO::getChunkIndex,
                        Comparator.nullsLast(Integer::compareTo)))
                .forEach(chunk -> appendChunkWithoutDuplicateOverlap(merged, chunk.getContent()));
        return merged.toString();
    }

    private void appendChunkWithoutDuplicateOverlap(StringBuilder merged, String nextContent) {
        if (nextContent == null || nextContent.isBlank()) {
            return;
        }
        if (merged.length() == 0) {
            merged.append(nextContent);
            return;
        }
        int overlapLength = findOverlapLength(merged, nextContent);
        if (overlapLength > 0) {
            merged.append(nextContent.substring(overlapLength));
            return;
        }
        merged.append('\n').append(nextContent);
    }

    private int findOverlapLength(StringBuilder merged, String nextContent) {
        int maxLength = Math.min(merged.length(), nextContent.length());
        for (int length = maxLength; length > 0; length--) {
            if (endsWith(merged, nextContent, length)) {
                return length;
            }
        }
        return 0;
    }

    private boolean endsWith(StringBuilder merged, String nextContent, int length) {
        int offset = merged.length() - length;
        for (int i = 0; i < length; i++) {
            if (merged.charAt(offset + i) != nextContent.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private ClothingSizeParseResult parseClothingSizeRows(String content) {
        Map<String, Integer> sizeCounts = initSizeCountMap();
        int total = 0;
        if (content == null || content.isBlank()) {
            return new ClothingSizeParseResult(sizeCounts, total);
        }
        for (String line : content.split("\\R")) {
            String normalizedLine = line == null ? "" : line.trim();
            if (normalizedLine.isBlank() || normalizedLine.startsWith("Sheet:")
                    || normalizedLine.startsWith("人员\t")) {
                continue;
            }
            String[] columns = normalizedLine.split("\\t", -1);
            if (columns.length < 2) {
                continue;
            }
            String name = columns[0].trim();
            String size = normalizeClothingSize(columns[1]);
            if (name.isBlank() || size == null) {
                continue;
            }
            sizeCounts.put(size, sizeCounts.getOrDefault(size, 0) + 1);
            total++;
        }
        sizeCounts.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0);
        return new ClothingSizeParseResult(sizeCounts, total);
    }

    private String normalizeClothingSize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return CLOTHING_SIZE_ORDER.contains(normalized) ? normalized : null;
    }

    private void appendClothingSizeCitationHits(Map<String, KnowledgeHit> citationHits, AiKnowledgeBaseDO knowledgeBase,
                                                List<AiDocumentChunkDO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (AiDocumentChunkDO chunk : chunks) {
            if (citationHits.size() >= CLOTHING_SIZE_CITATION_LIMIT) {
                return;
            }
            Map<String, Object> metadata = parseMetadata(chunk.getMetadataJson());
            citationHits.putIfAbsent(buildChunkCitationKey(chunk), KnowledgeHit.builder()
                    .tenantId(chunk.getTenantId())
                    .knowledgeBaseId(knowledgeBase.getId())
                    .documentId(chunk.getDocumentId())
                    .chunkId(chunk.getId())
                    .chunkNo(chunk.getChunkIndex())
                    .documentTitle(extractDocumentTitle(metadata))
                    .content(chunk.getContent())
                    .score(1.0D)
                    .metadata(metadata)
                    .build());
        }
    }

    private String buildChunkCitationKey(AiDocumentChunkDO chunk) {
        return "chunk:" + chunk.getId();
    }

    private List<KnowledgeHit> buildClothingSizeCitationHits(ClothingSizeCountResult result) {
        return result.citationHits().stream()
                .limit(CLOTHING_SIZE_CITATION_LIMIT)
                .toList();
    }

    private String buildClothingSizeCountAnswer(ClothingSizeCountResult result) {
        StringBuilder answer = new StringBuilder();
        answer.append("结论：按当前知识库中已解析的工装统计表逐行统计，共 **")
                .append(result.totalCount())
                .append(" 条**。\n\n");
        answer.append("| 工装尺寸 | 数量 |\n");
        answer.append("|---|---:|\n");
        for (String size : CLOTHING_SIZE_ORDER) {
            Integer count = result.sizeCounts().get(size);
            if (count != null && count > 0) {
                answer.append("| ").append(size).append(" | ").append(count).append(" |\n");
            }
        }
        answer.append("| **合计** | **").append(result.totalCount()).append("** |\n\n");
        answer.append("说明：本次统计不是让模型从少量引用片段里人工数表，而是把同一文档的所有 chunk 按 overlap 合并还原后，按“人员 / 工装尺寸”列逐行统计。");
        return answer.toString();
    }

    private String buildClothingSizeCountDebugInfo(String question, ClothingSizeCountResult result) {
        return """
                ## 工装尺寸统计调试信息
                - 用户问题：%s
                - 识别逻辑：命中“工装 + 尺寸 + 统计/数量/多少/对应”等问题意图后，跳过通用 topK 向量召回和模型人工计数。
                - 扫描方式：先找到包含“工装尺寸”表头的 chunk，再读取同一 documentId 下全部有效 chunk。
                - 合并方式：按 chunk_index 排序，自动移除切片 overlap，避免重复计数和半行截断。
                - 统计方式：按行解析“人员 / 工装尺寸”两列，识别 XS/S/M/L/XL/2XL...10XL。
                - 参与统计文档数：%d
                - 识别总条数：%d
                """.formatted(question, result.documentCount(), result.totalCount());
    }

    private boolean isClothingSizeCountQuestion(String question, String normalizedQuestion) {
        String source = ((question == null ? "" : question) + "\n"
                + (normalizedQuestion == null ? "" : normalizedQuestion));
        boolean clothingTerm = containsAnyIgnoreCase(source, List.of("工装", "衣服", "尺码"));
        boolean sizeTerm = containsAnyIgnoreCase(source, List.of("尺寸", "尺码", "size"));
        boolean countTerm = containsAnyIgnoreCase(source, List.of("统计", "数量", "多少", "合计", "对应", "count"));
        return clothingTerm && sizeTerm && countTerm;
    }

    private RagChatResponse tryAnswerClothingSizeCorrection(String question, List<AiChatMessageDO> conversationContext,
                                                            AiChatConversationDO conversation,
                                                            AiChatMessageDO userMessage, Long tenantId,
                                                            Long departmentId, Long userId, long startNanos) {
        String correctedSize = extractClothingSize(question);
        if (correctedSize == null || conversationContext == null || conversationContext.isEmpty()) {
            return null;
        }
        AiChatMessageDO latestAssistant = findLatestAssistantMessage(conversationContext);
        if (latestAssistant == null || latestAssistant.getContent() == null
                || !latestAssistant.getContent().contains("工装尺寸")) {
            return null;
        }
        if (!latestAssistant.getContent().contains("未纳入统计")
                && !latestAssistant.getContent().contains("未显示工装尺寸")
                && !latestAssistant.getContent().contains("已确认")) {
            return null;
        }
        if (latestAssistant.getContent().contains("已确认") && latestAssistant.getContent().contains(correctedSize)) {
            return saveDirectAnswer(conversation, userMessage, tenantId, departmentId, userId,
                    latestAssistant.getContent(), "命中上一轮工装尺寸修正结果，避免重复调用模型。", startNanos);
        }

        Map<String, Integer> sizeCounts = parseClothingSizeCounts(latestAssistant.getContent());
        if (sizeCounts.isEmpty()) {
            return null;
        }
        int total = sizeCounts.values().stream().reduce(0, Integer::sum);
        sizeCounts.put(correctedSize, sizeCounts.getOrDefault(correctedSize, 0) + 1);
        String answer = buildClothingSizeCorrectionAnswer(question, correctedSize, sizeCounts, total + 1);
        return saveDirectAnswer(conversation, userMessage, tenantId, departmentId, userId, answer,
                "命中工装尺寸人工修正，基于上一轮统计表格直接更新数量，跳过 embedding、向量检索和模型调用。", startNanos);
    }

    private AiChatMessageDO findLatestAssistantMessage(List<AiChatMessageDO> conversationContext) {
        for (int i = conversationContext.size() - 1; i >= 0; i--) {
            AiChatMessageDO message = conversationContext.get(i);
            if (message != null && ChatMessageRoleEnum.ASSISTANT.getCode().equals(message.getRole())) {
                return message;
            }
        }
        return null;
    }

    private String extractClothingSize(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        Matcher matcher = CLOTHING_SIZE_PATTERN.matcher(question.toUpperCase(Locale.ROOT));
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).toUpperCase(Locale.ROOT);
    }

    private Map<String, Integer> parseClothingSizeCounts(String content) {
        Map<String, Integer> sizeCounts = new LinkedHashMap<>();
        if (content == null || content.isBlank()) {
            return sizeCounts;
        }
        Matcher matcher = CLOTHING_SIZE_COUNT_PATTERN.matcher(content);
        while (matcher.find()) {
            String size = matcher.group(1).toUpperCase(Locale.ROOT);
            if (!CLOTHING_SIZE_ORDER.contains(size)) {
                continue;
            }
            sizeCounts.put(size, Integer.parseInt(matcher.group(2)));
        }
        return sizeCounts;
    }

    private String buildClothingSizeCorrectionAnswer(String question, String correctedSize,
                                                     Map<String, Integer> sizeCounts, int total) {
        StringBuilder answer = new StringBuilder();
        answer.append("结论：已按你的修正确认 `")
                .append(question == null ? "" : question.trim())
                .append("`。按上一轮统计口径修正后，工装合计更新为 **")
                .append(total)
                .append(" 件**，其中 **")
                .append(correctedSize)
                .append(" 更新为 ")
                .append(sizeCounts.get(correctedSize))
                .append(" 件**。\n\n");
        answer.append("| 工装尺寸 | 修正后数量 |\n");
        answer.append("|---|---:|\n");
        for (String size : CLOTHING_SIZE_ORDER) {
            Integer count = sizeCounts.get(size);
            if (count != null) {
                answer.append("| ").append(size).append(" | ").append(count).append(" |\n");
            }
        }
        answer.append("| **合计** | **").append(total).append("** |\n\n");
        answer.append("说明：这是基于上一轮统计结果和你本轮人工确认信息的快速修正；如果要把该修正长期纳入知识库，需要同步更新原始工装统计表后重新上传/解析/向量化。");
        return answer.toString();
    }

    private RagChatResponse tryAnswerTwoHaoHrLeaveEmployeeList(RagChatRequest request,
                                                               List<AiKnowledgeBaseDO> knowledgeBases,
                                                               AiChatConversationDO conversation,
                                                               AiChatMessageDO userMessage,
                                                               Long tenantId, Long departmentId, Long userId,
                                                               long startNanos, String effectiveQuestion) {
        if (!isTwoHaoHrLeaveEmployeeListQuestion(request.getQuestion(), effectiveQuestion)) {
            return null;
        }
        AiDataSourceDO dataSource = twoHaoHrAttendanceStatService.findTwoHaoHrDataSource(tenantId,
                knowledgeBases.stream().map(AiKnowledgeBaseDO::getId).toList());
        if (dataSource == null) {
            return null;
        }
        AttendanceDateRange dateRange = resolveAttendanceDateRange(effectiveQuestion);
        String objectType = resolveTwoHaoHrLeaveEmployeeObjectType(effectiveQuestion);
        LeaveEmployeeListResult result = twoHaoHrLeaveEmployeeListService.listEmployees(tenantId, dataSource,
                objectType, dateRange.startDate(), dateRange.endDate());
        String answer = buildTwoHaoHrLeaveEmployeeListAnswer(result);
        String debugInfo = "- twoHaoHrLeaveEmployeeList=true\n"
                + "- knowledgeBaseId=" + request.getKnowledgeBaseId() + "\n"
                + "- dataSourceId=" + dataSource.getId() + "\n"
                + "- objectType=" + objectType + "\n"
                + "- dateRange=" + formatDateRange(dateRange.startDate(), dateRange.endDate()) + "\n"
                + "- rawRecordCount=" + result.rawRecordCount() + "\n"
                + "- matchedCount=" + result.employees().size() + "\n"
                + "- elapsedMs=" + elapsedMillis(startNanos);
        log.info("RAG 2hao HR leave employee list answered directly, tenantId={}, departmentId={}, knowledgeBaseId={}, dataSourceId={}, conversationId={}, objectType={}, matchedCount={}, elapsedMs={}",
                tenantId, departmentId, dataSource.getKnowledgeBaseId(), dataSource.getId(), conversation.getId(),
                objectType, result.employees().size(), elapsedMillis(startNanos));
        return saveDirectAnswer(conversation, userMessage, tenantId, departmentId, userId, answer, debugInfo,
                startNanos);
    }

    private boolean isTwoHaoHrLeaveEmployeeListQuestion(String question, String effectiveQuestion) {
        String text = (question == null ? "" : question) + "\n"
                + (effectiveQuestion == null ? "" : effectiveQuestion);
        boolean leaveIntent = containsAnyLiteral(text, "离职", "待离职", "离任", "离岗");
        boolean listIntent = containsAnyLiteral(text, "名单", "列表", "明细", "清单", "人员", "姓名",
                "完整", "不要脱敏", "不脱敏", "全部展示");
        return leaveIntent && listIntent;
    }

    private String resolveTwoHaoHrLeaveEmployeeObjectType(String question) {
        String text = question == null ? "" : question;
        if (containsAnyLiteral(text, "待离职", "即将离职", "预离职")) {
            return TwoHaoHrLeaveEmployeeListService.LEAVING_EMPLOYEE_LIST;
        }
        return TwoHaoHrLeaveEmployeeListService.LEAVE_EMPLOYEE_LIST;
    }

    private String buildTwoHaoHrLeaveEmployeeListAnswer(LeaveEmployeeListResult result) {
        List<LeaveEmployee> employees = result.employees() == null ? Collections.emptyList() : result.employees();
        String objectName = TwoHaoHrLeaveEmployeeListService.LEAVING_EMPLOYEE_LIST.equals(result.objectType())
                ? "待离职员工" : "离职员工";
        StringBuilder builder = new StringBuilder();
        builder.append("### 2号人事部").append(objectName).append("名单\n\n");
        builder.append("- 查询对象：").append(objectName).append('\n');
        builder.append("- 查询范围：").append(formatDateRange(result.startDate(), result.endDate())).append('\n');
        builder.append("- 原始同步记录数：").append(formatCount(result.rawRecordCount())).append(" 条\n");
        builder.append("- 本次匹配人数：").append(formatCount(employees.size())).append(" 人\n");
        builder.append("- 姓名展示：未脱敏，来自 2号人事部 API 原始同步记录\n\n");
        if (employees.isEmpty()) {
            builder.append("未查询到匹配的").append(objectName).append("记录。");
            return builder.toString();
        }
        builder.append("| 序号 | 工号 | 姓名 | 离职日期 | 离职类型 | 离职原因 | 部门ID |\n");
        builder.append("| ---: | --- | --- | --- | --- | --- | --- |\n");
        for (int i = 0; i < employees.size(); i++) {
            LeaveEmployee employee = employees.get(i);
            builder.append("| ")
                    .append(i + 1)
                    .append(" | ")
                    .append(formatText(employee.employeeNo()))
                    .append(" | ")
                    .append(formatText(employee.name()))
                    .append(" | ")
                    .append(employee.leaveDate() == null ? "-" : employee.leaveDate())
                    .append(" | ")
                    .append(formatText(employee.leaveTypeName()))
                    .append(" | ")
                    .append(formatText(employee.leaveReason()))
                    .append(" | ")
                    .append(formatText(employee.departmentId()))
                    .append(" |\n");
        }
        return builder.toString();
    }

    private RagChatResponse tryAnswerTwoHaoHrAttendanceStat(RagChatRequest request,
                                                            List<AiKnowledgeBaseDO> knowledgeBases,
                                                            AiChatConversationDO conversation,
                                                            AiChatMessageDO userMessage,
                                                            Long tenantId, Long departmentId, Long userId,
                                                            String currentUserNickname, long startNanos, String effectiveQuestion,
                                                            String normalizedEffectiveQuestion) {
        AiDataSourceDO dataSource = twoHaoHrAttendanceStatService.findTwoHaoHrDataSource(tenantId,
                knowledgeBases.stream().map(AiKnowledgeBaseDO::getId).toList());
        if (dataSource == null) {
            return null;
        }
        PersonalAttendanceTarget personalTarget = resolveTwoHaoHrPersonalAttendanceTarget(request.getQuestion(),
                currentUserNickname);
        if (personalTarget != null) {
            if (!hasText(personalTarget.employeeId()) && !hasText(personalTarget.employeeName())) {
                String answer = buildTwoHaoHrPersonalAttendanceMissingUserAnswer();
                String debugInfo = "- twoHaoHrPersonalAttendance=true\n"
                        + "- dataSourceId=" + dataSource.getId() + "\n"
                        + "- reason=current user nickname is empty\n"
                        + "- elapsedMs=" + elapsedMillis(startNanos);
                return saveDirectAnswer(conversation, userMessage, tenantId, departmentId, userId, answer, debugInfo,
                        startNanos);
            }
            AttendanceDateRange dateRange = resolveAttendanceDateRange(request.getQuestion());
            TwoHaoHrAttendanceStatReqVO statReqVO = new TwoHaoHrAttendanceStatReqVO();
            statReqVO.setKnowledgeBaseId(dataSource.getKnowledgeBaseId());
            statReqVO.setDataSourceId(dataSource.getId());
            statReqVO.setEmployeeId(personalTarget.employeeId());
            statReqVO.setEmployeeName(personalTarget.employeeName());
            statReqVO.setEmployeeKeyword(request.getQuestion());
            statReqVO.setStartDate(dateRange.startDate());
            statReqVO.setEndDate(dateRange.endDate());
            TwoHaoHrAttendanceStatRespVO stat = twoHaoHrAttendanceStatService.getDepartmentStat(statReqVO);
            String answer = buildTwoHaoHrPersonalAttendanceStatAnswer(stat);
            String debugInfo = buildTwoHaoHrPersonalAttendanceStatDebugInfo(request, stat, request.getQuestion(),
                    elapsedMillis(startNanos));
            log.info("RAG 2hao HR personal attendance stat answered directly, tenantId={}, departmentId={}, knowledgeBaseId={}, dataSourceId={}, conversationId={}, employeeMatchType={}, totalRecords={}, elapsedMs={}",
                    tenantId, departmentId, dataSource.getKnowledgeBaseId(), dataSource.getId(), conversation.getId(),
                    stat.getEmployeeMatchType(), stat.getTotalRecords(), elapsedMillis(startNanos));
            return saveDirectAnswer(conversation, userMessage, tenantId, departmentId, userId, answer, debugInfo,
                    startNanos);
        }
        if (!isTwoHaoHrAttendanceStatQuestion(effectiveQuestion, normalizedEffectiveQuestion)) {
            return null;
        }
        AttendanceDateRange dateRange = resolveAttendanceDateRange(effectiveQuestion);
        TwoHaoHrAttendanceStatReqVO statReqVO = new TwoHaoHrAttendanceStatReqVO();
        statReqVO.setKnowledgeBaseId(dataSource.getKnowledgeBaseId());
        statReqVO.setDataSourceId(dataSource.getId());
        statReqVO.setDepartmentKeyword(effectiveQuestion);
        statReqVO.setStartDate(dateRange.startDate());
        statReqVO.setEndDate(dateRange.endDate());
        TwoHaoHrAttendanceStatRespVO stat = twoHaoHrAttendanceStatService.getDepartmentStat(statReqVO);
        String answer = buildTwoHaoHrAttendanceStatAnswer(stat, effectiveQuestion);
        String debugInfo = buildTwoHaoHrAttendanceStatDebugInfo(request, stat, effectiveQuestion, elapsedMillis(startNanos));
        log.info("RAG 2hao HR attendance stat answered directly, tenantId={}, departmentId={}, knowledgeBaseId={}, dataSourceId={}, conversationId={}, totalRecords={}, elapsedMs={}",
                tenantId, departmentId, dataSource.getKnowledgeBaseId(), dataSource.getId(), conversation.getId(),
                stat.getTotalRecords(), elapsedMillis(startNanos));
        return saveDirectAnswer(conversation, userMessage, tenantId, departmentId, userId, answer, debugInfo, startNanos);
    }

    private boolean isTwoHaoHrAttendanceStatQuestion(String question, String normalizedQuestion) {
        String text = question == null ? "" : question;
        String normalized = normalizedQuestion == null ? normalizeQuestion(text) : normalizedQuestion;
        boolean attendanceIntent = containsAnyLiteral(text, "考勤", "打卡", "出勤", "请假", "加班", "外勤",
                "迟到", "早退", "缺卡", "旷工");
        boolean statIntent = containsAnyLiteral(text, "统计", "汇总", "情况", "查询", "多少", "数据", "上月",
                "本月", "这个月", "上个月", "最近") || containsAnyLiteral(normalized, "count", "total", "sum");
        return attendanceIntent && statIntent;
    }

    private PersonalAttendanceTarget resolveTwoHaoHrPersonalAttendanceTarget(String question, String currentUserNickname) {
        String text = question == null ? "" : question.trim();
        if (text.isEmpty() || !containsAttendanceKeyword(text)) {
            return null;
        }
        if (isSelfPersonalAttendanceQuestion(text)) {
            return new PersonalAttendanceTarget(null, currentUserNickname == null ? null : currentUserNickname.trim());
        }
        if (hasText(currentUserNickname) && text.contains(currentUserNickname.trim())) {
            return new PersonalAttendanceTarget(null, currentUserNickname.trim());
        }
        Matcher matcher = PERSONAL_ATTENDANCE_NAME_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = cleanPersonalAttendanceName(matcher.group(1));
            if (looksLikePersonalAttendanceName(candidate)) {
                return new PersonalAttendanceTarget(null, candidate);
            }
        }
        return null;
    }

    private boolean isSelfPersonalAttendanceQuestion(String text) {
        return containsAnyLiteral(text, "\u6211\u7684\u8003\u52e4", "\u6211\u7684\u6253\u5361", "\u6211\u7684\u51fa\u52e4",
                "\u6211\u7684\u8bf7\u5047", "\u6211\u7684\u52a0\u73ed", "\u6211\u7684\u5916\u52e4",
                "\u672c\u4eba\u8003\u52e4", "\u672c\u4eba\u6253\u5361", "\u672c\u4eba\u51fa\u52e4",
                "\u81ea\u5df1\u7684\u8003\u52e4", "\u767b\u5f55\u7528\u6237\u7684\u8003\u52e4",
                "\u5f53\u524d\u7528\u6237\u7684\u8003\u52e4");
    }

    private String cleanPersonalAttendanceName(String candidate) {
        String result = candidate == null ? "" : candidate.trim();
        for (String prefix : List.of("帮我查一下", "帮忙查一下", "查询一下", "查看一下", "统计一下", "汇总一下",
                "帮我查", "帮忙查", "查询", "查看", "统计", "汇总", "查下", "看下", "查", "看", "请")) {
            if (result.startsWith(prefix) && result.length() > prefix.length()) {
                result = result.substring(prefix.length()).trim();
            }
        }
        while (result.endsWith("的") && result.length() > 2) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private boolean containsAttendanceKeyword(String text) {
        return containsAnyLiteral(text, "\u8003\u52e4", "\u6253\u5361", "\u51fa\u52e4", "\u8bf7\u5047", "\u52a0\u73ed",
                "\u5916\u52e4", "\u8fdf\u5230", "\u65e9\u9000", "\u7f3a\u5361", "\u65f7\u5de5");
    }

    private boolean looksLikePersonalAttendanceName(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() >= 2 && text.length() <= 4
                && !PERSONAL_ATTENDANCE_NAME_STOP_WORDS.contains(text)
                && !looksLikeOrganizationName(text);
    }

    private boolean looksLikeOrganizationName(String value) {
        String text = value == null ? "" : value.trim();
        return ORGANIZATION_NAME_MARKERS.stream().anyMatch(text::contains);
    }

    private String buildTwoHaoHrPersonalAttendanceMissingUserAnswer() {
        return """
                ### 2号人事部个人考勤统计

                未能从当前登录上下文确认要查询的员工姓名，请在问题中写明员工姓名后重试。
                """;
    }

    private AttendanceDateRange resolveAttendanceDateRange(String question) {
        LocalDate today = LocalDate.now();
        String text = question == null ? "" : question;
        if (containsAnyLiteral(text, "今天", "今日")) {
            return new AttendanceDateRange(today, today);
        }
        if (containsAnyLiteral(text, "昨天", "昨日")) {
            LocalDate yesterday = today.minusDays(1);
            return new AttendanceDateRange(yesterday, yesterday);
        }
        if (containsAnyLiteral(text, "上月", "上个月")) {
            YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
            return new AttendanceDateRange(lastMonth.atDay(1), lastMonth.atEndOfMonth());
        }
        if (containsAnyLiteral(text, "本月", "这个月", "当月")) {
            YearMonth currentMonth = YearMonth.from(today);
            return new AttendanceDateRange(currentMonth.atDay(1), today);
        }
        if (containsAnyLiteral(text, "最近30天", "近30天")) {
            return new AttendanceDateRange(today.minusDays(29), today);
        }
        Matcher yearMonthMatcher = YEAR_MONTH_PATTERN.matcher(text);
        if (yearMonthMatcher.find()) {
            int year = Integer.parseInt(yearMonthMatcher.group(1));
            int month = Integer.parseInt(yearMonthMatcher.group(2));
            if (month >= 1 && month <= 12) {
                YearMonth yearMonth = YearMonth.of(year, month);
                return new AttendanceDateRange(yearMonth.atDay(1), yearMonth.atEndOfMonth());
            }
        }
        Matcher monthMatcher = MONTH_PATTERN.matcher(text);
        if (monthMatcher.find()) {
            int month = Integer.parseInt(monthMatcher.group(1));
            if (month >= 1 && month <= 12) {
                int year = month > today.getMonthValue() ? today.getYear() - 1 : today.getYear();
                YearMonth yearMonth = YearMonth.of(year, month);
                return new AttendanceDateRange(yearMonth.atDay(1), yearMonth.atEndOfMonth());
            }
        }
        return new AttendanceDateRange(null, null);
    }

    private String buildTwoHaoHrAttendanceStatAnswer(TwoHaoHrAttendanceStatRespVO stat, String question) {
        List<TwoHaoHrAttendanceStatRespVO.TypeStat> typeStats = stat.getTypeStats() == null
                ? Collections.emptyList() : stat.getTypeStats();
        List<TwoHaoHrAttendanceStatRespVO.DepartmentStat> departmentStats = stat.getDepartmentStats() == null
                ? Collections.emptyList() : stat.getDepartmentStats();
        StringBuilder builder = new StringBuilder();
        builder.append("### 2号人事部部门考勤统计\n\n");
        builder.append("- 统计范围：").append(formatDateRange(stat)).append('\n');
        builder.append("- 部门匹配：").append(formatDepartmentScope(stat)).append('\n');
        builder.append("- 总记录数：").append(formatCount(stat.getTotalRecords())).append(" 条\n");
        builder.append("- 涉及员工数：").append(formatCount(stat.getEmployeeCount())).append(" 人\n");
        if (stat.getMinAttendanceDate() != null || stat.getMaxAttendanceDate() != null) {
            builder.append("- 实际数据日期：")
                    .append(stat.getMinAttendanceDate() == null ? "-" : stat.getMinAttendanceDate())
                    .append(" 至 ")
                    .append(stat.getMaxAttendanceDate() == null ? "-" : stat.getMaxAttendanceDate())
                    .append('\n');
        }
        builder.append("\n| 考勤类型 | 记录数 | 涉及员工 |\n");
        builder.append("| --- | ---: | ---: |\n");
        for (TwoHaoHrAttendanceStatRespVO.TypeStat typeStat : typeStats) {
            builder.append("| ")
                    .append(typeStat.getRecordTypeName())
                    .append(" | ")
                    .append(formatCount(typeStat.getRecordCount()))
                    .append(" | ")
                    .append(formatCount(typeStat.getEmployeeCount()))
                    .append(" |\n");
        }
        if (stat.getDailyStats() != null && !stat.getDailyStats().isEmpty()) {
            builder.append("\n| 日期 | 总记录 | 打卡 | 打卡结果 | 请假 | 加班 | 外勤 |\n");
            builder.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            stat.getDailyStats().stream().limit(31).forEach(daily -> builder.append("| ")
                    .append(daily.getAttendanceDate())
                    .append(" | ")
                    .append(formatCount(daily.getTotalRecords()))
                    .append(" | ")
                    .append(formatCount(daily.getCardRecordCount()))
                    .append(" | ")
                    .append(formatCount(daily.getCardResultCount()))
                    .append(" | ")
                    .append(formatCount(daily.getLeaveCount()))
                    .append(" | ")
                    .append(formatCount(daily.getOvertimeCount()))
                    .append(" | ")
                    .append(formatCount(daily.getOutingCount()))
                    .append(" |\n"));
        }
        if (shouldShowDepartmentBreakdown(question, stat, departmentStats)) {
            builder.append("\n各部门考勤明细如下：\n\n");
            builder.append("| 部门 | 总记录 | 涉及员工 | 打卡 | 打卡结果 | 请假 | 加班 | 外勤 | 排班 |\n");
            builder.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            departmentStats.stream().limit(50).forEach(department -> builder.append("| ")
                    .append(formatDepartmentName(department))
                    .append(" | ")
                    .append(formatCount(department.getRecordCount()))
                    .append(" | ")
                    .append(formatCount(department.getEmployeeCount()))
                    .append(" | ")
                    .append(formatCount(department.getCardRecordCount()))
                    .append(" | ")
                    .append(formatCount(department.getCardResultCount()))
                    .append(" | ")
                    .append(formatCount(department.getLeaveCount()))
                    .append(" | ")
                    .append(formatCount(department.getOvertimeCount()))
                    .append(" | ")
                    .append(formatCount(department.getOutingCount()))
                    .append(" | ")
                    .append(formatCount(department.getShiftCount()))
                    .append(" |\n"));
            if (departmentStats.size() > 50) {
                builder.append("\n仅展示记录数最高的 50 个部门；如需完整清单，请缩小部门或日期范围后重查。\n");
            }
        }
        if (!shouldShowDepartmentBreakdown(question, stat, departmentStats)
                && stat.getMatchedDepartmentCount() != null && stat.getMatchedDepartmentCount() > 1) {
            builder.append("\n匹配到多个部门，以上为名称包含目标词的合并统计。匹配部门 Top 5：\n\n");
            departmentStats.stream().limit(5).forEach(department -> builder.append("- ")
                    .append(department.getDepartmentName() == null || department.getDepartmentName().isBlank()
                            ? department.getDepartmentId() : department.getDepartmentName())
                    .append("：")
                    .append(formatCount(department.getRecordCount()))
                    .append(" 条\n"));
        }
        builder.append("\n说明：结果来自 2号人事部考勤明细表的结构化聚合，不返回个人打卡明细、证件、薪资等敏感字段。");
        return builder.toString();
    }

    private String buildTwoHaoHrPersonalAttendanceStatAnswer(TwoHaoHrAttendanceStatRespVO stat) {
        List<TwoHaoHrAttendanceStatRespVO.TypeStat> typeStats = stat.getTypeStats() == null
                ? Collections.emptyList() : stat.getTypeStats();
        List<TwoHaoHrAttendanceStatRespVO.DepartmentStat> departmentStats = stat.getDepartmentStats() == null
                ? Collections.emptyList() : stat.getDepartmentStats();
        StringBuilder builder = new StringBuilder();
        builder.append("### 2号人事部个人考勤统计\n\n");
        builder.append("- 查询对象：").append(formatEmployeeScope(stat)).append('\n');
        builder.append("- 统计范围：").append(formatDateRange(stat)).append('\n');
        builder.append("- 总记录数：").append(formatCount(stat.getTotalRecords())).append(" 条\n");
        builder.append("- 匹配员工数：").append(formatCount(stat.getEmployeeCount())).append(" 人\n");
        if (stat.getMinAttendanceDate() != null || stat.getMaxAttendanceDate() != null) {
            builder.append("- 实际数据日期：")
                    .append(stat.getMinAttendanceDate() == null ? "-" : stat.getMinAttendanceDate())
                    .append(" 至 ")
                    .append(stat.getMaxAttendanceDate() == null ? "-" : stat.getMaxAttendanceDate())
                    .append('\n');
        }
        if (stat.getEmployeeCount() != null && stat.getEmployeeCount() > 1
                && "EMPLOYEE_NAME_CONTAINS".equals(stat.getEmployeeMatchType())) {
            builder.append("\n提示：当前按姓名包含进行模糊匹配，匹配到多名员工；如需精确到单人，请使用员工 ID 或更完整姓名。\n");
        }
        if (formatCount(stat.getTotalRecords()).equals("0")) {
            builder.append("\n未查询到匹配的考勤聚合记录。\n");
        }
        builder.append("\n| 考勤类型 | 记录数 | 涉及员工 |\n");
        builder.append("| --- | ---: | ---: |\n");
        for (TwoHaoHrAttendanceStatRespVO.TypeStat typeStat : typeStats) {
            builder.append("| ")
                    .append(typeStat.getRecordTypeName())
                    .append(" | ")
                    .append(formatCount(typeStat.getRecordCount()))
                    .append(" | ")
                    .append(formatCount(typeStat.getEmployeeCount()))
                    .append(" |\n");
        }
        if (stat.getDailyStats() != null && !stat.getDailyStats().isEmpty()) {
            builder.append("\n| 日期 | 总记录 | 打卡 | 打卡结果 | 请假 | 加班 | 外勤 |\n");
            builder.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            stat.getDailyStats().stream().limit(31).forEach(daily -> builder.append("| ")
                    .append(daily.getAttendanceDate())
                    .append(" | ")
                    .append(formatCount(daily.getTotalRecords()))
                    .append(" | ")
                    .append(formatCount(daily.getCardRecordCount()))
                    .append(" | ")
                    .append(formatCount(daily.getCardResultCount()))
                    .append(" | ")
                    .append(formatCount(daily.getLeaveCount()))
                    .append(" | ")
                    .append(formatCount(daily.getOvertimeCount()))
                    .append(" | ")
                    .append(formatCount(daily.getOutingCount()))
                    .append(" |\n"));
        }
        if (!departmentStats.isEmpty()) {
            builder.append("\n所属部门分布如下：\n\n");
            builder.append("| 部门 | 总记录 | 涉及员工 | 打卡 | 打卡结果 | 请假 | 加班 | 外勤 | 排班 |\n");
            builder.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            departmentStats.stream().limit(20).forEach(department -> builder.append("| ")
                    .append(formatDepartmentName(department))
                    .append(" | ")
                    .append(formatCount(department.getRecordCount()))
                    .append(" | ")
                    .append(formatCount(department.getEmployeeCount()))
                    .append(" | ")
                    .append(formatCount(department.getCardRecordCount()))
                    .append(" | ")
                    .append(formatCount(department.getCardResultCount()))
                    .append(" | ")
                    .append(formatCount(department.getLeaveCount()))
                    .append(" | ")
                    .append(formatCount(department.getOvertimeCount()))
                    .append(" | ")
                    .append(formatCount(department.getOutingCount()))
                    .append(" | ")
                    .append(formatCount(department.getShiftCount()))
                    .append(" |\n"));
        }
        builder.append("\n说明：结果来自 2号人事部考勤明细表的结构化聚合，不返回个人打卡时间明细、证件、薪资等敏感字段。");
        return builder.toString();
    }

    private boolean shouldShowDepartmentBreakdown(String question, TwoHaoHrAttendanceStatRespVO stat,
                                                  List<TwoHaoHrAttendanceStatRespVO.DepartmentStat> departmentStats) {
        if (departmentStats == null || departmentStats.isEmpty()) {
            return false;
        }
        String text = (question == null ? "" : question) + "\n"
                + (stat.getDepartmentKeyword() == null ? "" : stat.getDepartmentKeyword());
        return containsAnyLiteral(text, "\u5404\u90e8\u95e8", "\u4e0b\u7ea7\u90e8\u95e8", "\u4e0b\u5c5e\u90e8\u95e8",
                "\u5b50\u90e8\u95e8", "\u5206\u522b", "\u6309\u90e8\u95e8", "\u6bcf\u4e2a\u90e8\u95e8",
                "\u90e8\u95e8\u660e\u7ec6", "\u90e8\u95e8\u5217\u8868");
    }

    private String formatDepartmentName(TwoHaoHrAttendanceStatRespVO.DepartmentStat department) {
        if (department == null) {
            return "-";
        }
        if (hasText(department.getDepartmentName())) {
            return department.getDepartmentName();
        }
        return hasText(department.getDepartmentId()) ? department.getDepartmentId() : "-";
    }

    private String formatDateRange(TwoHaoHrAttendanceStatRespVO stat) {
        return formatDateRange(stat.getStartDate(), stat.getEndDate());
    }

    private String formatDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return "全部已同步日期";
        }
        return (startDate == null ? "-" : startDate) + " 至 "
                + (endDate == null ? "-" : endDate);
    }

    private String formatDepartmentScope(TwoHaoHrAttendanceStatRespVO stat) {
        if ("DEPARTMENT_ID".equals(stat.getDepartmentMatchType())) {
            return "部门ID " + stat.getDepartmentId();
        }
        if ("DEPARTMENT_NAME_CONTAINS".equals(stat.getDepartmentMatchType())) {
            return "部门名称包含 `" + stat.getDepartmentName() + "`，匹配 "
                    + formatCount(stat.getMatchedDepartmentCount()) + " 个部门";
        }
        return "全部部门";
    }

    private String formatEmployeeScope(TwoHaoHrAttendanceStatRespVO stat) {
        if ("EMPLOYEE_ID".equals(stat.getEmployeeMatchType())) {
            return "员工ID/OA编码 `" + stat.getEmployeeId() + "`";
        }
        if ("EMPLOYEE_NAME_CONTAINS".equals(stat.getEmployeeMatchType())) {
            return "员工姓名包含 `" + stat.getEmployeeName() + "`";
        }
        return "未指定员工";
    }

    private String buildTwoHaoHrAttendanceStatDebugInfo(RagChatRequest request, TwoHaoHrAttendanceStatRespVO stat,
                                                        String effectiveQuestion, long elapsedMs) {
        return "- twoHaoHrAttendanceStat=true\n"
                + "- knowledgeBaseId=" + request.getKnowledgeBaseId() + "\n"
                + "- dataSourceId=" + stat.getDataSourceId() + "\n"
                + "- effectiveQuestion=" + effectiveQuestion + "\n"
                + "- departmentMatchType=" + stat.getDepartmentMatchType() + "\n"
                + "- departmentName=" + stat.getDepartmentName() + "\n"
                + "- dateRange=" + formatDateRange(stat) + "\n"
                + "- totalRecords=" + stat.getTotalRecords() + "\n"
                + "- elapsedMs=" + elapsedMs;
    }

    private String buildTwoHaoHrPersonalAttendanceStatDebugInfo(RagChatRequest request,
                                                                TwoHaoHrAttendanceStatRespVO stat,
                                                                String question, long elapsedMs) {
        return "- twoHaoHrPersonalAttendanceStat=true\n"
                + "- knowledgeBaseId=" + request.getKnowledgeBaseId() + "\n"
                + "- dataSourceId=" + stat.getDataSourceId() + "\n"
                + "- question=" + question + "\n"
                + "- employeeMatchType=" + stat.getEmployeeMatchType() + "\n"
                + "- employeeName=" + stat.getEmployeeName() + "\n"
                + "- dateRange=" + formatDateRange(stat) + "\n"
                + "- totalRecords=" + stat.getTotalRecords() + "\n"
                + "- elapsedMs=" + elapsedMs;
    }

    private String formatCount(Number value) {
        return String.valueOf(value == null ? 0L : value.longValue());
    }

    private String formatText(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim().replace("|", "\\|").replace("\n", " ");
    }

    private boolean containsAnyLiteral(String text, String... fragments) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String fragment : fragments) {
            if (fragment != null && !fragment.isBlank() && text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private RagChatResponse saveDirectAnswer(AiChatConversationDO conversation, AiChatMessageDO userMessage,
                                              Long tenantId, Long departmentId, Long userId, String answer,
                                              String debugInfo, long startNanos) {
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), answer, null, 0L);
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);
        log.info("RAG chat answered directly, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, elapsedMs={}",
                tenantId, departmentId, conversation.getKnowledgeBaseId(), conversation.getId(), elapsedMillis(startNanos));
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(answer)
                .noContext(false)
                .debugInfo(debugInfo)
                .citations(Collections.emptyList())
                .build();
    }

    private RagChatResponse tryAnswerTableInventory(RagChatRequest request, List<AiKnowledgeBaseDO> knowledgeBases,
                                                    AiChatConversationDO conversation, AiChatMessageDO userMessage,
                                                    Long tenantId, Long departmentId, Long userId,
                                                    long startNanos, String effectiveQuestion) {
        TableInventoryResult inventory = collectTableInventory(tenantId, knowledgeBases);
        if (inventory.tableHits().isEmpty()) {
            return null;
        }
        String answer = buildTableInventoryAnswer(inventory.tableHits());
        AiChatMessageDO assistantMessage = saveMessage(tenantId, departmentId, conversation.getId(), userId,
                ChatMessageRoleEnum.ASSISTANT.getCode(), answer, null, 0L);
        List<RagChatCitation> citations = saveCitations(tenantId, departmentId, assistantMessage.getId(),
                request.getKnowledgeBaseId(), buildTableInventoryCitationHits(inventory.tableHits()));
        updateConversationLastMessageTime(conversation.getId(), tenantId, departmentId);
        log.info("RAG table inventory answered directly, tenantId={}, departmentId={}, knowledgeBaseId={}, conversationId={}, tableCount={}, scannedChunkCount={}, citationCount={}, elapsedMs={}",
                tenantId, departmentId, request.getKnowledgeBaseId(), conversation.getId(), inventory.tableHits().size(),
                inventory.scannedChunkCount(), citations.size(), elapsedMillis(startNanos));
        return RagChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessage.getId())
                .answer(answer)
                .noContext(false)
                .debugInfo(buildTableInventoryDebugInfo(effectiveQuestion, inventory))
                .citations(citations)
                .build();
    }

    private TableInventoryResult collectTableInventory(Long tenantId, List<AiKnowledgeBaseDO> knowledgeBases) {
        Map<String, KnowledgeHit> tableHits = new LinkedHashMap<>();
        int scannedChunkCount = 0;
        for (AiKnowledgeBaseDO knowledgeBase : knowledgeBases) {
            List<AiDocumentChunkDO> chunks = documentChunkMapper.selectTableInventoryCandidates(tenantId,
                    knowledgeBase.getId(), TABLE_INVENTORY_CHUNK_LIMIT);
            if (chunks == null || chunks.isEmpty()) {
                continue;
            }
            scannedChunkCount += chunks.size();
            for (AiDocumentChunkDO chunk : chunks) {
                List<String> tableNames = extractTableNames(chunk.getContent());
                if (tableNames.isEmpty()) {
                    continue;
                }
                for (String tableName : tableNames) {
                    tableHits.putIfAbsent(tableName, toTableInventoryHit(knowledgeBase, chunk, tableName));
                }
            }
        }
        return new TableInventoryResult(tableHits, scannedChunkCount);
    }

    private List<String> extractTableNames(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        List<String> tableNames = new ArrayList<>();
        Matcher matcher = TABLE_NAME_PATTERN.matcher(content);
        while (matcher.find()) {
            String tableName = normalizeTableName(matcher.group(1));
            if (!tableName.isBlank() && !tableNames.contains(tableName)) {
                tableNames.add(tableName);
            }
        }
        return tableNames;
    }

    private String normalizeTableName(String tableName) {
        if (tableName == null) {
            return "";
        }
        return tableName.trim()
                .replaceAll("^[`'\"“”‘’]+", "")
                .replaceAll("[`'\"“”‘’，,。；;：:]+$", "");
    }

    private KnowledgeHit toTableInventoryHit(AiKnowledgeBaseDO knowledgeBase, AiDocumentChunkDO chunk,
                                             String tableName) {
        Map<String, Object> metadata = parseMetadata(chunk.getMetadataJson());
        String documentTitle = extractDocumentTitle(metadata);
        return KnowledgeHit.builder()
                .tenantId(chunk.getTenantId())
                .knowledgeBaseId(knowledgeBase.getId())
                .documentId(chunk.getDocumentId())
                .chunkId(chunk.getId())
                .chunkNo(chunk.getChunkIndex())
                .documentTitle(documentTitle == null ? knowledgeBase.getName() : documentTitle)
                .content("表名：" + tableName + "\n\n来源切片：\n" + chunk.getContent())
                .score(1.0D)
                .metadata(metadata)
                .build();
    }

    private String buildTableInventoryAnswer(Map<String, KnowledgeHit> tableHits) {
        StringBuilder answer = new StringBuilder();
        answer.append("根据当前知识库资料，识别到 MES 数据表共 ")
                .append(tableHits.size())
                .append(" 张。");
        answer.append("\n\n数据表清单：\n");
        int index = 1;
        for (String tableName : tableHits.keySet()) {
            answer.append(index++).append(". `").append(tableName).append("`\n");
        }
        answer.append("\n说明：本次统计依据为已解析文档切片中的 `表：xxx` 标记；如果原始文档后续有新增或删除表，需要重新解析并向量化后再统计。");
        return answer.toString();
    }

    private List<KnowledgeHit> buildTableInventoryCitationHits(Map<String, KnowledgeHit> tableHits) {
        return tableHits.values().stream()
                .limit(TABLE_INVENTORY_CITATION_LIMIT)
                .toList();
    }

    private String buildTableInventoryDebugInfo(String question, TableInventoryResult inventory) {
        return """
                ## 数据表清单统计调试信息
                - 用户问题：%s
                - 识别逻辑：命中“数据表/表结构/表名 + 统计/数量/哪些/清单”等问题意图后，跳过通用 topK 向量召回，直接扫描当前可访问知识库中已解析成功的 chunk。
                - 扫描条件：tenantId、knowledgeBaseId、chunk.status=SUCCESS，且 chunk 内容包含 `表：`。
                - 表名提取规则：正则 `表：\\s*([^\\s]+)`，按出现顺序去重。
                - 扫描 chunk 数：%d
                - 识别表数量：%d
                - 说明：该分支用于全量清单/数量类问题，避免通用 RAG 只取少量 topK 片段导致漏表。
                """.formatted(question, inventory.scannedChunkCount(), inventory.tableHits().size());
    }

    private boolean isTableInventoryQuestion(String question, String normalizedQuestion) {
        String source = ((question == null ? "" : question) + "\n"
                + (normalizedQuestion == null ? "" : normalizedQuestion)).toLowerCase(Locale.ROOT);
        boolean tableTerm = containsAnyIgnoreCase(source, List.of("数据表", "数据库表", "表结构", "表名", "table", "tables"));
        boolean inventoryTerm = containsAnyIgnoreCase(source, List.of("哪些", "有哪些", "有那些", "数量", "多少",
                "统计", "清单", "列表", "几个", "count", "list"));
        return tableTerm && inventoryTerm;
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
            Long citationKnowledgeBaseId = cachedCitation.getKnowledgeBaseId() == null
                    ? knowledgeBaseId : cachedCitation.getKnowledgeBaseId();
            AiChatCitationDO citation = AiChatCitationDO.builder()
                    .tenantId(tenantId)
                    .departmentId(departmentId)
                    .messageId(assistantMessageId)
                    .knowledgeBaseId(citationKnowledgeBaseId)
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
                    .knowledgeBaseId(citationKnowledgeBaseId)
                    .documentId(cachedCitation.getDocumentId())
                    .chunkId(cachedCitation.getChunkId())
                    .chunkNo(cachedCitation.getChunkNo())
                    .documentTitle(cachedCitation.getDocumentTitle())
                    .score(cachedCitation.getScore())
                    .quoteText(citation.getQuoteText())
                    .meetingId(cachedCitation.getMeetingId())
                    .documentType(cachedCitation.getDocumentType())
                    .projectCode(cachedCitation.getProjectCode())
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

    private List<RagChatCitation> saveExternalCitations(Long tenantId, Long departmentId, Long assistantMessageId,
                                                        Long fallbackKnowledgeBaseId,
                                                        List<RagChatCitation> sourceCitations) {
        return saveExternalCitations(tenantId, departmentId, assistantMessageId, fallbackKnowledgeBaseId,
                sourceCitations, 0);
    }

    private List<RagChatCitation> saveExternalCitations(Long tenantId, Long departmentId, Long assistantMessageId,
                                                        Long fallbackKnowledgeBaseId,
                                                        List<RagChatCitation> sourceCitations,
                                                        int sortOrderOffset) {
        if (sourceCitations == null || sourceCitations.isEmpty()) {
            return Collections.emptyList();
        }
        List<RagChatCitation> citations = new ArrayList<>(sourceCitations.size());
        for (int i = 0; i < sourceCitations.size(); i++) {
            RagChatCitation sourceCitation = sourceCitations.get(i);
            if (sourceCitation == null) {
                continue;
            }
            // FastGPT 返回的 datasetId/collectionId 属于外部平台，不能当作本地知识库 ID 关联。
            Long citationKnowledgeBaseId = fallbackKnowledgeBaseId;
            AiChatCitationDO citation = AiChatCitationDO.builder()
                    .tenantId(tenantId)
                    .departmentId(departmentId)
                    .messageId(assistantMessageId)
                    .knowledgeBaseId(citationKnowledgeBaseId)
                    .externalKnowledgeBaseName(truncate(sourceCitation.getKnowledgeBaseName(), 128))
                    .documentId(sourceCitation.getDocumentId())
                    .chunkId(sourceCitation.getChunkId())
                    .documentTitle(sourceCitation.getDocumentTitle())
                    .score(toBigDecimal(sourceCitation.getScore()))
                    .sortOrder(sortOrderOffset + citations.size() + 1)
                    .contentSnapshot(truncate(sourceCitation.getQuoteText(), QUOTE_TEXT_MAX_LENGTH))
                    .quoteText(truncate(sourceCitation.getQuoteText(), QUOTE_TEXT_MAX_LENGTH))
                    .build();
            chatCitationMapper.insert(citation);
            citations.add(RagChatCitation.builder()
                    .knowledgeBaseId(citationKnowledgeBaseId)
                    .knowledgeBaseName(sourceCitation.getKnowledgeBaseName())
                    .documentId(sourceCitation.getDocumentId())
                    .chunkId(sourceCitation.getChunkId())
                    .chunkNo(sourceCitation.getChunkNo())
                    .documentTitle(sourceCitation.getDocumentTitle())
                    .score(sourceCitation.getScore())
                    .quoteText(citation.getQuoteText())
                    .meetingId(sourceCitation.getMeetingId())
                    .documentType(sourceCitation.getDocumentType())
                    .projectCode(sourceCitation.getProjectCode())
                    .build());
        }
        return citations;
    }

    private List<RagChatCitation> saveCitations(Long tenantId, Long departmentId, Long assistantMessageId,
                                                Long knowledgeBaseId, List<KnowledgeHit> hits) {
        return saveCitations(tenantId, departmentId, assistantMessageId, knowledgeBaseId, hits, 0);
    }

    private List<RagChatCitation> saveCitations(Long tenantId, Long departmentId, Long assistantMessageId,
                                                Long knowledgeBaseId, List<KnowledgeHit> hits,
                                                int sortOrderOffset) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<RagChatCitation> citations = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            KnowledgeHit hit = hits.get(i);
            Long citationKnowledgeBaseId = hit.getKnowledgeBaseId() == null ? knowledgeBaseId : hit.getKnowledgeBaseId();
            AiChatCitationDO citation = AiChatCitationDO.builder()
                    .tenantId(tenantId)
                    .departmentId(departmentId)
                    .messageId(assistantMessageId)
                    .knowledgeBaseId(citationKnowledgeBaseId)
                    .documentId(hit.getDocumentId())
                    .chunkId(hit.getChunkId())
                    .documentTitle(hit.getDocumentTitle())
                    .score(toBigDecimal(hit.getScore()))
                    .sortOrder(sortOrderOffset + i + 1)
                    .contentSnapshot(truncate(hit.getContent(), QUOTE_TEXT_MAX_LENGTH))
                    .quoteText(truncate(hit.getContent(), QUOTE_TEXT_MAX_LENGTH))
                    .build();
            chatCitationMapper.insert(citation);
            citations.add(RagChatCitation.builder()
                    .knowledgeBaseId(citationKnowledgeBaseId)
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

    private boolean isWebSearchEnabled() {
        return aiProperties.getRag() == null || !Boolean.FALSE.equals(aiProperties.getRag().getEnableWebSearch());
    }

    private int resolveWebSearchTopK() {
        Integer topK = aiProperties.getRag() == null ? null : aiProperties.getRag().getWebSearchTopK();
        if (topK == null || topK <= 0) {
            return DEFAULT_WEB_SEARCH_TOP_K;
        }
        return Math.min(topK, 10);
    }

    private List<KnowledgeHit> appendWebSearchHits(String question, List<KnowledgeHit> hits, Long tenantId,
                                                   Long knowledgeBaseId) {
        List<WebSearchResult> webResults = webSearchService.search(question, resolveWebSearchTopK());
        if (webResults == null || webResults.isEmpty()) {
            return hits == null ? Collections.emptyList() : hits;
        }
        Map<String, KnowledgeHit> merged = new LinkedHashMap<>();
        appendHits(merged, hits);
        for (int i = 0; i < webResults.size(); i++) {
            appendHits(merged, List.of(toWebSearchHit(webResults.get(i), i + 1, tenantId, knowledgeBaseId)));
        }
        log.info("RAG web search appended, tenantId={}, knowledgeBaseId={}, resultCount={}",
                tenantId, knowledgeBaseId, webResults.size());
        return new ArrayList<>(merged.values());
    }

    /**
     * 统计、汇总、数量类问题不能只依赖 topK 片段，否则 Excel/表格文档容易出现漏行、半行和重复行。
     * 这里在进入 PromptBuilder 前，把命中的结构化文档扩展为同一 documentId 下的完整有效 chunk。
     */
    private List<KnowledgeHit> expandStructuredDocumentHitsForStatisticalQuestion(String question,
                                                                                  List<KnowledgeHit> hits,
                                                                                  Long tenantId) {
        if (!isStatisticalQuestion(question) || hits == null || hits.isEmpty()) {
            return hits == null ? Collections.emptyList() : hits;
        }
        Map<String, KnowledgeHit> expandedHits = new LinkedHashMap<>();
        Set<String> expandedDocumentKeys = new LinkedHashSet<>();
        for (KnowledgeHit hit : hits) {
            if (!isExpandableStructuredDocumentHit(hit)) {
                continue;
            }
            String documentKey = buildDocumentKey(hit);
            if (!expandedDocumentKeys.add(documentKey)) {
                continue;
            }
            KnowledgeHit expandedHit = buildExpandedStructuredDocumentHit(hit, tenantId);
            if (expandedHit != null) {
                expandedHits.put(buildHitKey(expandedHit), expandedHit);
            } else {
                expandedDocumentKeys.remove(documentKey);
            }
        }
        if (expandedHits.isEmpty()) {
            return hits;
        }
        for (KnowledgeHit hit : hits) {
            if (hit == null || expandedDocumentKeys.contains(buildDocumentKey(hit))) {
                continue;
            }
            expandedHits.putIfAbsent(buildHitKey(hit), hit);
        }
        log.info("RAG structured document expanded for statistical question, tenantId={}, expandedDocumentCount={}, beforeHitCount={}, afterHitCount={}",
                tenantId, expandedDocumentKeys.size(), hits.size(), expandedHits.size());
        return new ArrayList<>(expandedHits.values());
    }

    private boolean isStatisticalQuestion(String question) {
        return containsAnyIgnoreCase(question, STATISTICAL_QUESTION_KEYWORDS);
    }

    private boolean isExpandableStructuredDocumentHit(KnowledgeHit hit) {
        if (hit == null || hit.getDocumentId() == null || hit.getKnowledgeBaseId() == null) {
            return false;
        }
        String searchText = buildHitSearchText(hit);
        if (containsAnyIgnoreCase(searchText, STRUCTURED_DOCUMENT_KEYWORDS)) {
            return true;
        }
        Map<String, Object> metadata = hit.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }
        String metadataText = metadata.toString();
        return containsAnyIgnoreCase(metadataText, List.of("ExcelDocumentParser", "Workbook", "sheetCount",
                "rowCount", ".xls", ".xlsx", ".xlsb", "application/vnd.ms-excel",
                "spreadsheetml.sheet"));
    }

    private KnowledgeHit buildExpandedStructuredDocumentHit(KnowledgeHit seedHit, Long tenantId) {
        List<AiDocumentChunkDO> chunks = documentChunkMapper.selectListByDocumentIdAndTenantId(
                seedHit.getDocumentId(), seedHit.getKnowledgeBaseId(), tenantId);
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        List<AiDocumentChunkDO> safeChunks = chunks.stream()
                .filter(chunk -> chunk != null && ChunkStatusEnum.SUCCESS.getCode().equals(chunk.getStatus())
                        && chunk.getContent() != null && !chunk.getContent().isBlank())
                .sorted(Comparator.comparing(AiDocumentChunkDO::getChunkIndex,
                        Comparator.nullsLast(Integer::compareTo)))
                .limit(STRUCTURED_DOCUMENT_EXPANSION_MAX_CHUNKS)
                .toList();
        String content = mergeChunkContents(safeChunks);
        if (content.isBlank()) {
            return null;
        }
        boolean truncated = content.length() > STRUCTURED_DOCUMENT_EXPANSION_MAX_CHARS;
        if (truncated) {
            content = content.substring(0, STRUCTURED_DOCUMENT_EXPANSION_MAX_CHARS)
                    + "\n[结构化文档内容超过当前上下文保护阈值，已截断；生产环境应转入结构化查询引擎处理]";
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (seedHit.getMetadata() != null) {
            metadata.putAll(seedHit.getMetadata());
        }
        metadata.put("structuredDocumentExpanded", true);
        metadata.put("sourceChunkCount", safeChunks.size());
        metadata.put("sourceDocumentId", seedHit.getDocumentId());
        metadata.put("truncated", truncated);
        return KnowledgeHit.builder()
                .vectorId("structured-doc:" + seedHit.getKnowledgeBaseId() + ":" + seedHit.getDocumentId())
                .tenantId(seedHit.getTenantId())
                .knowledgeBaseId(seedHit.getKnowledgeBaseId())
                .documentId(seedHit.getDocumentId())
                .chunkId(null)
                .chunkNo(null)
                .documentTitle(seedHit.getDocumentTitle())
                .content(content)
                .score(seedHit.getScore() == null ? 1.0D : seedHit.getScore())
                .metadata(metadata)
                .build();
    }

    private String buildDocumentKey(KnowledgeHit hit) {
        if (hit == null) {
            return "";
        }
        return "kb:" + hit.getKnowledgeBaseId() + ":document:" + hit.getDocumentId();
    }

    private List<KnowledgeHit> expandHitsByRetrievalPlan(RetrievalPlan retrievalPlan, List<KnowledgeHit> hits,
                                                         Long tenantId) {
        if (retrievalPlan == null || hits == null || hits.isEmpty()) {
            return hits == null ? Collections.emptyList() : hits;
        }
        if (RetrievalModeEnum.STRUCTURED_QUERY.equals(retrievalPlan.getMode())) {
            return expandStructuredDocumentHitsForStatisticalQuestion("统计", hits, tenantId);
        }
        // 全文、章节、相邻片段扩展会在检索计划分类稳定后逐步接入执行器。
        return hits;
    }

    private KnowledgeHit toWebSearchHit(WebSearchResult result, int index, Long tenantId, Long knowledgeBaseId) {
        String title = result.getTitle() == null || result.getTitle().isBlank()
                ? "Web Search Result" : result.getTitle().trim();
        String url = result.getUrl() == null ? "" : result.getUrl().trim();
        String content = buildWebSearchContent(title, url, result.getSnippet());
        return KnowledgeHit.builder()
                .vectorId("web:" + sha256Hex(url.isBlank() ? title + ":" + index : url))
                .tenantId(tenantId)
                .knowledgeBaseId(knowledgeBaseId)
                .documentTitle("联网搜索：" + title)
                .content(content)
                .score(Math.max(0.01D, WEB_SEARCH_SCORE - index * 0.01D))
                .metadata(Map.of("sourceType", "WEB_SEARCH", "url", url, "title", title))
                .build();
    }

    private String buildWebSearchContent(String title, String url, String snippet) {
        StringBuilder content = new StringBuilder();
        content.append("标题：").append(title);
        if (url != null && !url.isBlank()) {
            content.append("\nURL：").append(url);
        }
        if (snippet != null && !snippet.isBlank()) {
            content.append("\n摘要：").append(snippet.trim());
        }
        return truncate(content.toString(), QUOTE_TEXT_MAX_LENGTH);
    }

    private List<KnowledgeHit> searchKnowledge(String question, RagChatRequest request,
                                               List<AiKnowledgeBaseDO> knowledgeBases,
                                               Long tenantId, Long departmentId) {
        List<Double> queryEmbedding = aiEmbeddingService.embed(question);
        Map<String, KnowledgeHit> merged = new LinkedHashMap<>();
        for (AiKnowledgeBaseDO knowledgeBase : knowledgeBases) {
            KnowledgeSearchRequest searchRequest = KnowledgeSearchRequest.builder()
                    .tenantId(tenantId)
                    .departmentId(departmentId)
                    .knowledgeBaseId(knowledgeBase.getId())
                    .queryEmbedding(queryEmbedding)
                    .topK(resolveTopK(request, knowledgeBase))
                    .scoreThreshold(resolveScoreThreshold(request, knowledgeBase))
                    .build();
            appendHits(merged, knowledgeVectorStore.search(searchRequest));
            appendHits(merged, lexicalFallbackSearch(question, searchRequest));
        }
        return selectTopMergedHits(new ArrayList<>(merged.values()), request, knowledgeBases.get(0),
                knowledgeBases.size());
    }

    private List<KnowledgeHit> mergeKnowledgeHits(List<KnowledgeHit> semanticHits, List<KnowledgeHit> lexicalHits) {
        Map<String, KnowledgeHit> merged = new LinkedHashMap<>();
        appendHits(merged, semanticHits);
        appendHits(merged, lexicalHits);
        return new ArrayList<>(merged.values());
    }

    private List<KnowledgeHit> selectTopMergedHits(List<KnowledgeHit> hits, RagChatRequest request,
                                                   AiKnowledgeBaseDO primaryKnowledgeBase, int knowledgeBaseCount) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        if (knowledgeBaseCount <= 1) {
            return hits;
        }
        int topK = resolveTopK(request, primaryKnowledgeBase);
        int limit = Math.min(Math.max(topK * Math.max(knowledgeBaseCount, 1), topK), 100);
        return hits.stream()
                .limit(limit)
                .toList();
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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

    private record TableInventoryResult(Map<String, KnowledgeHit> tableHits, int scannedChunkCount) {
    }

    private record ClothingSizeParseResult(Map<String, Integer> sizeCounts, int totalCount) {
    }

    private record ClothingSizeCountResult(Map<String, Integer> sizeCounts, int totalCount, int documentCount,
                                           List<KnowledgeHit> citationHits) {
    }

    private record FastGptCallOutcome(FastGptRagResult result, AiChatModelResponse modelResponse, long latencyMs,
                                      boolean skipped, String errorMessage) {
    }

    private record PersonalAttendanceTarget(String employeeId, String employeeName) {
    }

    private record AttendanceDateRange(LocalDate startDate, LocalDate endDate) {
    }

}
