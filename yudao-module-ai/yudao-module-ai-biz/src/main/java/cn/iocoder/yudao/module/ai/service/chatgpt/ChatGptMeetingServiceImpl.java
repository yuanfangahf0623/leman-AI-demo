package cn.iocoder.yudao.module.ai.service.chatgpt;

import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingDetailRespVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingMinutesRespVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingRagSearchReqVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingRagSearchRespVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingSearchReqVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingSearchRespVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingTranscriptRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingActionItemDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingMinutesDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingTranscriptDO;
import cn.iocoder.yudao.module.ai.dal.mysql.chatgpt.AiMeetingActionItemMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.chatgpt.AiMeetingMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.chatgpt.AiMeetingMinutesMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.chatgpt.AiMeetingTranscriptMapper;
import cn.iocoder.yudao.module.ai.framework.chatgpt.AiChatGptActionsProperties;
import cn.iocoder.yudao.module.ai.framework.chatgpt.ChatGptActionAuditContextHolder;
import cn.iocoder.yudao.module.ai.framework.chatgpt.ChatGptCallerContext;
import cn.iocoder.yudao.module.ai.framework.chatgpt.ChatGptCallerContextHolder;
import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptMeetingKnowledgeSearchRequestDTO;
import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptMeetingKnowledgeSearchResultDTO;
import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptMeetingSearchQueryDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatGptMeetingServiceImpl implements ChatGptMeetingService {

    private static final int MAX_SEARCH_LIMIT = 20;
    private static final int MAX_TRANSCRIPT_CHARS = 50_000;
    private static final int MAX_RAG_TOP_K = 10;
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String UNKNOWN = "\u672A\u660E\u786E";

    private final AiChatGptActionsProperties properties;
    private final AiMeetingMapper meetingMapper;
    private final AiMeetingMinutesMapper meetingMinutesMapper;
    private final AiMeetingActionItemMapper meetingActionItemMapper;
    private final AiMeetingTranscriptMapper meetingTranscriptMapper;
    private final ChatGptMeetingPermissionService permissionService;
    private final ChatGptSensitiveMaskService sensitiveMaskService;
    private final ChatGptMeetingKnowledgeSearchClient knowledgeSearchClient;
    private final ObjectMapper objectMapper;

    @Override
    public ChatGptMeetingSearchRespVO searchMeetings(ChatGptMeetingSearchReqVO reqVO) {
        ChatGptActionAuditContextHolder.setActionName("searchMeetings");
        ChatGptActionAuditContextHolder.setQueryText(reqVO.getKeyword());
        ChatGptCallerContext caller = ChatGptCallerContextHolder.required();
        int limit = clamp(reqVO.getLimit(), 10, MAX_SEARCH_LIMIT);
        List<AiMeetingDO> meetings = meetingMapper.selectChatGptVisibleList(ChatGptMeetingSearchQueryDTO.builder()
                        .keyword(reqVO.getKeyword())
                        .startDate(reqVO.getStartDate())
                        .endDate(reqVO.getEndDate())
                        .organizer(reqVO.getOrganizer())
                        .projectCode(reqVO.getProjectCode())
                        .build(),
                caller.getTenantId(),
                permissionService.getMvpAccessibleSensitivityLevels(), limit);
        List<ChatGptMeetingSearchRespVO.Item> items = meetings.stream()
                .filter(meeting -> permissionService.canAccessMeeting(meeting, caller))
                .map(this::buildSearchItem)
                .toList();
        return ChatGptMeetingSearchRespVO.builder().items(items).build();
    }

    @Override
    public ChatGptMeetingDetailRespVO getMeeting(Long meetingId) {
        ChatGptActionAuditContextHolder.setActionName("getMeeting");
        ChatGptActionAuditContextHolder.setMeetingId(meetingId);
        AiMeetingDO meeting = getAccessibleMeetingOrThrow(meetingId);
        return buildDetail(meeting);
    }

    @Override
    public ChatGptMeetingMinutesRespVO getMeetingMinutes(Long meetingId) {
        ChatGptActionAuditContextHolder.setActionName("getMeetingMinutes");
        ChatGptActionAuditContextHolder.setMeetingId(meetingId);
        ChatGptCallerContext caller = ChatGptCallerContextHolder.required();
        AiMeetingDO meeting = getAccessibleMeetingOrThrow(meetingId);
        AiMeetingMinutesDO minutes = meetingMinutesMapper.selectByMeetingId(caller.getTenantId(), meetingId);
        List<AiMeetingActionItemDO> actionItems = meetingActionItemMapper.selectListByMeetingId(caller.getTenantId(), meetingId);
        return buildMinutes(meeting, minutes, actionItems, caller);
    }

    @Override
    public ChatGptMeetingTranscriptRespVO getMeetingTranscript(Long meetingId, Integer maxChars) {
        ChatGptActionAuditContextHolder.setActionName("getMeetingTranscript");
        ChatGptActionAuditContextHolder.setMeetingId(meetingId);
        ChatGptCallerContext caller = ChatGptCallerContextHolder.required();
        AiMeetingDO meeting = getAccessibleMeetingOrThrow(meetingId);
        if (!permissionService.canReturnTranscript(meeting, caller)) {
            ChatGptActionAuditContextHolder.setErrorCode("TRANSCRIPT_ACCESS_DENIED");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Meeting transcript is not allowed for this sensitivity level");
        }
        AiMeetingTranscriptDO transcript = meetingTranscriptMapper.selectByMeetingId(caller.getTenantId(), meetingId);
        String content = transcript == null ? "" : firstText(transcript.getCleanedContent(), transcript.getContent());
        int limit = clamp(maxChars, properties.getMaxTranscriptChars(), MAX_TRANSCRIPT_CHARS);
        return ChatGptMeetingTranscriptRespVO.builder()
                .meetingId(meetingId)
                .content(limit(mask(content), limit))
                .build();
    }

    @Override
    public ChatGptMeetingRagSearchRespVO ragSearch(ChatGptMeetingRagSearchReqVO reqVO) {
        ChatGptActionAuditContextHolder.setActionName("searchMeetingKnowledge");
        ChatGptActionAuditContextHolder.setKnowledgeBaseId(reqVO.getKnowledgeBaseId());
        ChatGptActionAuditContextHolder.setQueryText(reqVO.getQuery());
        ChatGptCallerContext caller = ChatGptCallerContextHolder.required();
        int topK = clamp(reqVO.getTopK(), properties.getDefaultTopK(), MAX_RAG_TOP_K);
        List<ChatGptMeetingKnowledgeSearchResultDTO> results;
        try {
            results = knowledgeSearchClient.search(ChatGptMeetingKnowledgeSearchRequestDTO.builder()
                    .tenantId(caller.getTenantId())
                    .query(reqVO.getQuery())
                    .knowledgeBaseId(reqVO.getKnowledgeBaseId())
                    .startDate(reqVO.getStartDate())
                    .endDate(reqVO.getEndDate())
                    .projectCode(reqVO.getProjectCode())
                    .topK(topK)
                    .build());
        } catch (Exception ex) {
            ChatGptActionAuditContextHolder.setErrorCode("MEETING_KNOWLEDGE_SEARCH_FAILED");
            log.warn("[ragSearch][meeting knowledge search failed, tenantId={}, knowledgeBaseId={}]",
                    caller.getTenantId(), reqVO.getKnowledgeBaseId(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Meeting knowledge search failed");
        }
        List<ChatGptMeetingRagSearchRespVO.AnswerContext> answerContext = new ArrayList<>();
        for (ChatGptMeetingKnowledgeSearchResultDTO result : results) {
            if (result.getMeetingId() == null || answerContext.size() >= topK) {
                continue;
            }
            AiMeetingDO meeting = meetingMapper.selectAccessibleById(result.getMeetingId(), caller.getTenantId(),
                    permissionService.getMvpAccessibleSensitivityLevels());
            if (!permissionService.canAccessMeeting(meeting, caller) || !matchesRagFilters(meeting, reqVO, result)) {
                continue;
            }
            answerContext.add(ChatGptMeetingRagSearchRespVO.AnswerContext.builder()
                    .meetingId(meeting.getId())
                    .documentType(result.getDocumentType())
                    .subject(mask(meeting.getSubject()))
                    .startTime(meeting.getStartTime())
                    .chunk(mask(result.getChunk()))
                    .score(result.getScore())
                    .build());
        }
        return ChatGptMeetingRagSearchRespVO.builder().answerContext(answerContext).build();
    }

    private AiMeetingDO getAccessibleMeetingOrThrow(Long meetingId) {
        ChatGptCallerContext caller = ChatGptCallerContextHolder.required();
        AiMeetingDO meeting = meetingMapper.selectAccessibleById(meetingId, caller.getTenantId(),
                permissionService.getMvpAccessibleSensitivityLevels());
        if (!permissionService.canAccessMeeting(meeting, caller)) {
            ChatGptActionAuditContextHolder.setErrorCode("MEETING_NOT_FOUND_OR_DENIED");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Meeting not found");
        }
        ChatGptActionAuditContextHolder.setKnowledgeBaseId(meeting.getKnowledgeBaseId());
        return meeting;
    }

    private ChatGptMeetingSearchRespVO.Item buildSearchItem(AiMeetingDO meeting) {
        return ChatGptMeetingSearchRespVO.Item.builder()
                .meetingId(meeting.getId())
                .subject(mask(meeting.getSubject()))
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .organizerName(mask(meeting.getOrganizerName()))
                .sourceType(meeting.getSourceType())
                .projectCode(meeting.getProjectCode())
                .hasTranscript(isSuccessStatus(meeting.getTranscriptStatus()))
                .hasMinutes(isSuccessStatus(meeting.getMinutesStatus()))
                .summary(mask(meeting.getSummary()))
                .build();
    }

    private ChatGptMeetingDetailRespVO buildDetail(AiMeetingDO meeting) {
        return ChatGptMeetingDetailRespVO.builder()
                .meetingId(meeting.getId())
                .subject(mask(meeting.getSubject()))
                .organizerName(mask(meeting.getOrganizerName()))
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .sourceType(meeting.getSourceType())
                .projectCode(meeting.getProjectCode())
                .minutesStatus(meeting.getMinutesStatus())
                .transcriptStatus(meeting.getTranscriptStatus())
                .knowledgeBaseId(meeting.getKnowledgeBaseId())
                .build();
    }

    private ChatGptMeetingMinutesRespVO buildMinutes(AiMeetingDO meeting, AiMeetingMinutesDO minutes,
                                                     List<AiMeetingActionItemDO> actionItemDOList,
                                                     ChatGptCallerContext caller) {
        List<ChatGptMeetingMinutesRespVO.ActionItem> actionItems = actionItemDOList == null || actionItemDOList.isEmpty()
                ? parseList(minutes == null ? null : minutes.getActionItemsJson(),
                        new TypeReference<List<ChatGptMeetingMinutesRespVO.ActionItem>>() {})
                : actionItemDOList.stream().map(this::buildActionItem).toList();
        return ChatGptMeetingMinutesRespVO.builder()
                .meetingId(meeting.getId())
                .title(mask(firstText(minutes == null ? null : minutes.getTitle(), meeting.getSubject())))
                .summary(mask(firstText(minutes == null ? null : minutes.getSummary(), meeting.getSummary())))
                .keyPoints(maskList(parseList(minutes == null ? null : minutes.getKeyPointsJson(),
                        new TypeReference<List<String>>() {})))
                .decisions(maskDecisions(parseList(minutes == null ? null : minutes.getDecisionsJson(),
                        new TypeReference<List<ChatGptMeetingMinutesRespVO.Decision>>() {}), meeting, caller))
                .actionItems(maskActionItems(actionItems, meeting, caller))
                .risks(maskRisks(parseList(minutes == null ? null : minutes.getRisksJson(),
                        new TypeReference<List<ChatGptMeetingMinutesRespVO.Risk>>() {}), meeting, caller))
                .openQuestions(maskList(parseList(minutes == null ? null : minutes.getOpenQuestionsJson(),
                        new TypeReference<List<String>>() {})))
                .build();
    }

    private ChatGptMeetingMinutesRespVO.ActionItem buildActionItem(AiMeetingActionItemDO actionItemDO) {
        return ChatGptMeetingMinutesRespVO.ActionItem.builder()
                .task(actionItemDO.getTask())
                .owner(firstText(actionItemDO.getOwner(), UNKNOWN))
                .deadline(actionItemDO.getDeadline() == null ? firstText(actionItemDO.getDeadlineText(), UNKNOWN)
                        : actionItemDO.getDeadline().toString())
                .priority(actionItemDO.getPriority())
                .sourceQuote(actionItemDO.getSourceQuote())
                .build();
    }

    private List<String> maskList(List<String> list) {
        return list.stream().map(this::mask).toList();
    }

    private List<ChatGptMeetingMinutesRespVO.Decision> maskDecisions(List<ChatGptMeetingMinutesRespVO.Decision> decisions,
                                                                     AiMeetingDO meeting, ChatGptCallerContext caller) {
        return decisions.stream()
                .map(decision -> ChatGptMeetingMinutesRespVO.Decision.builder()
                        .decision(mask(decision.getDecision()))
                        .context(mask(decision.getContext()))
                        .sourceQuote(maskSourceQuote(decision.getSourceQuote(), meeting, caller))
                        .build())
                .toList();
    }

    private List<ChatGptMeetingMinutesRespVO.ActionItem> maskActionItems(List<ChatGptMeetingMinutesRespVO.ActionItem> actionItems,
                                                                        AiMeetingDO meeting, ChatGptCallerContext caller) {
        return actionItems.stream()
                .map(actionItem -> ChatGptMeetingMinutesRespVO.ActionItem.builder()
                        .task(mask(actionItem.getTask()))
                        .owner(mask(firstText(actionItem.getOwner(), UNKNOWN)))
                        .deadline(mask(firstText(actionItem.getDeadline(), UNKNOWN)))
                        .priority(actionItem.getPriority())
                        .sourceQuote(maskSourceQuote(actionItem.getSourceQuote(), meeting, caller))
                        .build())
                .toList();
    }

    private List<ChatGptMeetingMinutesRespVO.Risk> maskRisks(List<ChatGptMeetingMinutesRespVO.Risk> risks,
                                                             AiMeetingDO meeting, ChatGptCallerContext caller) {
        return risks.stream()
                .map(risk -> ChatGptMeetingMinutesRespVO.Risk.builder()
                        .risk(mask(risk.getRisk()))
                        .impact(mask(risk.getImpact()))
                        .owner(mask(firstText(risk.getOwner(), UNKNOWN)))
                        .sourceQuote(maskSourceQuote(risk.getSourceQuote(), meeting, caller))
                        .build())
                .toList();
    }

    private String maskSourceQuote(String sourceQuote, AiMeetingDO meeting, ChatGptCallerContext caller) {
        if (!permissionService.canReturnSourceQuote(meeting, caller)) {
            return null;
        }
        return mask(sourceQuote);
    }

    private <T> List<T> parseList(String json, TypeReference<List<T>> typeReference) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<T> values = objectMapper.readValue(json, typeReference);
            return values == null ? List.of() : values;
        } catch (Exception ex) {
            log.warn("[parseList][minutes json parse failed]", ex);
            return List.of();
        }
    }

    private boolean matchesRagFilters(AiMeetingDO meeting, ChatGptMeetingRagSearchReqVO reqVO,
                                      ChatGptMeetingKnowledgeSearchResultDTO result) {
        if (reqVO.getKnowledgeBaseId() != null
                && !Objects.equals(reqVO.getKnowledgeBaseId(), firstLong(result.getKnowledgeBaseId(), meeting.getKnowledgeBaseId()))) {
            return false;
        }
        if (reqVO.getStartDate() != null && isBefore(meeting, reqVO.getStartDate())) {
            return false;
        }
        if (reqVO.getEndDate() != null && isAfter(meeting, reqVO.getEndDate())) {
            return false;
        }
        return !StringUtils.hasText(reqVO.getProjectCode()) || Objects.equals(reqVO.getProjectCode(), meeting.getProjectCode());
    }

    private static boolean isBefore(AiMeetingDO meeting, LocalDate startDate) {
        return meeting.getStartTime() != null && meeting.getStartTime().toLocalDate().isBefore(startDate);
    }

    private static boolean isAfter(AiMeetingDO meeting, LocalDate endDate) {
        return meeting.getStartTime() != null && meeting.getStartTime().toLocalDate().isAfter(endDate);
    }

    private static Long firstLong(Long first, Long second) {
        return first != null ? first : second;
    }

    private String mask(String text) {
        return sensitiveMaskService.mask(text);
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static boolean isSuccessStatus(String status) {
        return STATUS_SUCCESS.equalsIgnoreCase(status);
    }

    private static int clamp(Integer value, Integer defaultValue, int max) {
        int candidate = value == null ? (defaultValue == null ? max : defaultValue) : value;
        return Math.max(1, Math.min(candidate, max));
    }

    private static String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

}
