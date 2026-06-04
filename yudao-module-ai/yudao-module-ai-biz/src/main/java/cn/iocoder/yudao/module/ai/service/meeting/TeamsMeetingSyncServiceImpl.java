package cn.iocoder.yudao.module.ai.service.meeting;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.controller.admin.meeting.vo.AiTeamsMeetingSyncReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.meeting.vo.AiTeamsMeetingSyncRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingActionItemDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingMinutesDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingTranscriptDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiKnowledgeBaseMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.chatgpt.AiMeetingActionItemMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.chatgpt.AiMeetingMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.chatgpt.AiMeetingMinutesMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.chatgpt.AiMeetingTranscriptMapper;
import cn.iocoder.yudao.module.ai.framework.meeting.AiTeamsMeetingProperties;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.meeting.dto.MeetingKnowledgeDocumentDTO;
import cn.iocoder.yudao.module.ai.service.meeting.dto.TeamsMeetingDTO;
import cn.iocoder.yudao.module.ai.service.meeting.dto.TeamsMeetingMinutesDTO;
import cn.iocoder.yudao.module.ai.service.meeting.dto.TeamsTranscriptDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamsMeetingSyncServiceImpl implements TeamsMeetingSyncService {

    private static final String SOURCE_TYPE_TEAMS = "TEAMS";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_EMPTY = "EMPTY";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String DOCUMENT_TYPE_TRANSCRIPT = "meeting_transcript";
    private static final String DOCUMENT_TYPE_MINUTES = "meeting_minutes";
    private static final int DEFAULT_LIMIT = 20;

    private final AiTeamsMeetingProperties properties;
    private final TeamsMeetingGraphClient graphClient;
    private final TeamsMeetingTranscriptCleaner transcriptCleaner;
    private final TeamsMeetingMinutesGenerator minutesGenerator;
    private final MeetingKnowledgeIngestService knowledgeIngestService;
    private final AiMeetingMapper meetingMapper;
    private final AiMeetingTranscriptMapper transcriptMapper;
    private final AiMeetingMinutesMapper minutesMapper;
    private final AiMeetingActionItemMapper actionItemMapper;
    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AiTeamsMeetingSyncRespVO syncMeetings(AiTeamsMeetingSyncReqVO reqVO) {
        validateEnabled();
        validateRequest(reqVO);
        Long tenantId = AiTenantContextHolder.getTenantId();
        Long knowledgeBaseId = resolveKnowledgeBaseId(reqVO);
        validateKnowledgeBase(tenantId, knowledgeBaseId);
        String organizerUserId = resolveOrganizerUserId(reqVO);
        int limit = resolveLimit(reqVO);

        List<TeamsMeetingDTO> meetings = graphClient.listMeetings(organizerUserId, reqVO.getStartDate(),
                reqVO.getEndDate(), limit);
        List<AiTeamsMeetingSyncRespVO.Item> items = new ArrayList<>(meetings.size());
        int synced = 0;
        int skipped = 0;
        int failed = 0;
        for (TeamsMeetingDTO meeting : meetings) {
            try {
                AiTeamsMeetingSyncRespVO.Item item = syncOne(tenantId, organizerUserId, knowledgeBaseId, reqVO, meeting);
                items.add(item);
                if (STATUS_SKIPPED.equals(item.getAction())) {
                    skipped++;
                } else {
                    synced++;
                }
            } catch (Exception ex) {
                failed++;
                log.warn("Teams meeting sync item failed, tenantId={}, sourceMeetingId={}, errorType={}",
                        tenantId, meeting == null ? null : meeting.getSourceMeetingId(), ex.getClass().getSimpleName());
                items.add(AiTeamsMeetingSyncRespVO.Item.builder()
                        .sourceMeetingId(meeting == null ? null : meeting.getSourceMeetingId())
                        .subject(meeting == null ? null : meeting.getSubject())
                        .action(STATUS_FAILED)
                        .errorCode(ex.getClass().getSimpleName())
                        .build());
            }
        }
        return AiTeamsMeetingSyncRespVO.builder()
                .scannedCount(meetings.size())
                .syncedCount(synced)
                .skippedCount(skipped)
                .failedCount(failed)
                .items(items)
                .build();
    }

    private AiTeamsMeetingSyncRespVO.Item syncOne(Long tenantId, String organizerUserId, Long knowledgeBaseId,
                                                  AiTeamsMeetingSyncReqVO reqVO, TeamsMeetingDTO graphMeeting) {
        String sourceMeetingId = requiredText(graphMeeting.getSourceMeetingId(), "Graph meeting id is empty");
        Optional<TeamsTranscriptDTO> transcriptOptional = graphClient.getTranscript(organizerUserId, graphMeeting);
        String rawTranscript = transcriptOptional.map(TeamsTranscriptDTO::getContent).orElse("");
        String cleanedTranscript = transcriptCleaner.clean(rawTranscript, resolveTranscriptMaxChars());
        String transcriptStatus = StringUtils.hasText(cleanedTranscript) ? STATUS_SUCCESS : STATUS_EMPTY;
        TeamsMeetingMinutesDTO minutes = minutesGenerator.generate(graphMeeting, cleanedTranscript);
        String minutesStatus = minutes == null ? STATUS_EMPTY : STATUS_SUCCESS;

        AiMeetingDO meeting = upsertMeeting(tenantId, knowledgeBaseId, reqVO, graphMeeting, minutes,
                transcriptStatus, minutesStatus);
        upsertTranscript(tenantId, meeting.getId(), transcriptOptional.orElse(null), rawTranscript, cleanedTranscript);
        upsertMinutes(tenantId, meeting.getId(), minutes);
        Long transcriptDocumentId = ingestTranscriptDocument(tenantId, knowledgeBaseId, reqVO, meeting,
                cleanedTranscript);
        Long minutesDocumentId = ingestMinutesDocument(tenantId, knowledgeBaseId, reqVO, meeting, minutes);
        meetingMapper.updateDocumentIdsByIdAndTenantId(meeting.getId(), tenantId, transcriptDocumentId,
                minutesDocumentId);
        return AiTeamsMeetingSyncRespVO.Item.builder()
                .sourceMeetingId(sourceMeetingId)
                .meetingId(meeting.getId())
                .subject(meeting.getSubject())
                .action("UPSERT")
                .transcriptStatus(transcriptStatus)
                .minutesStatus(minutesStatus)
                .transcriptDocumentId(transcriptDocumentId)
                .minutesDocumentId(minutesDocumentId)
                .build();
    }

    private AiMeetingDO upsertMeeting(Long tenantId, Long knowledgeBaseId, AiTeamsMeetingSyncReqVO reqVO,
                                      TeamsMeetingDTO graphMeeting, TeamsMeetingMinutesDTO minutes,
                                      String transcriptStatus, String minutesStatus) {
        AiMeetingDO oldMeeting = meetingMapper.selectBySourceMeetingId(tenantId, SOURCE_TYPE_TEAMS,
                graphMeeting.getSourceMeetingId());
        AiMeetingDO meeting = new AiMeetingDO();
        meeting.setTenantId(tenantId);
        meeting.setSubject(limit(firstText(graphMeeting.getSubject(), "Teams meeting"), 255));
        meeting.setSummary(limit(minutes == null ? null : minutes.getSummary(), 1000));
        meeting.setOrganizerName(limit(firstText(graphMeeting.getOrganizerName(), graphMeeting.getOrganizerEmail()), 255));
        meeting.setStartTime(graphMeeting.getStartTime());
        meeting.setEndTime(graphMeeting.getEndTime());
        meeting.setSourceType(SOURCE_TYPE_TEAMS);
        meeting.setSourceMeetingId(graphMeeting.getSourceMeetingId());
        meeting.setSourceOnlineMeetingId(graphMeeting.getOnlineMeetingId());
        meeting.setKnowledgeBaseId(knowledgeBaseId);
        meeting.setMinutesStatus(minutesStatus);
        meeting.setTranscriptStatus(transcriptStatus);
        meeting.setSyncStatus(STATUS_SUCCESS);
        meeting.setErrorMessage(null);
        meeting.setChatgptVisible(resolveChatGptVisible(reqVO));
        meeting.setSensitivityLevel(resolveSensitivityLevel(reqVO));
        meeting.setProjectCode(limit(reqVO.getProjectCode(), 128));
        meeting.setDeleted(false);
        if (oldMeeting == null) {
            meetingMapper.insert(meeting);
            return meeting;
        }
        meeting.setId(oldMeeting.getId());
        meetingMapper.updateSyncByIdAndTenantId(meeting, tenantId);
        return meeting;
    }

    private void upsertTranscript(Long tenantId, Long meetingId, TeamsTranscriptDTO transcript, String rawContent,
                                  String cleanedContent) {
        AiMeetingTranscriptDO oldTranscript = transcriptMapper.selectByMeetingId(tenantId, meetingId);
        AiMeetingTranscriptDO transcriptDO = new AiMeetingTranscriptDO();
        transcriptDO.setTenantId(tenantId);
        transcriptDO.setMeetingId(meetingId);
        transcriptDO.setSourceTranscriptId(transcript == null ? null : transcript.getTranscriptId());
        transcriptDO.setContent(rawContent);
        transcriptDO.setCleanedContent(cleanedContent);
        if (oldTranscript == null) {
            transcriptMapper.insert(transcriptDO);
        } else {
            transcriptMapper.updateByMeetingId(tenantId, transcriptDO);
        }
    }

    private void upsertMinutes(Long tenantId, Long meetingId, TeamsMeetingMinutesDTO minutes) {
        if (minutes == null) {
            return;
        }
        AiMeetingMinutesDO oldMinutes = minutesMapper.selectByMeetingId(tenantId, meetingId);
        AiMeetingMinutesDO minutesDO = new AiMeetingMinutesDO();
        minutesDO.setTenantId(tenantId);
        minutesDO.setMeetingId(meetingId);
        minutesDO.setTitle(minutes.getTitle());
        minutesDO.setSummary(minutes.getSummary());
        minutesDO.setKeyPointsJson(toJson(minutes.getKeyPoints()));
        minutesDO.setDecisionsJson(toJson(minutes.getDecisions()));
        minutesDO.setActionItemsJson(toJson(minutes.getActionItems()));
        minutesDO.setRisksJson(toJson(minutes.getRisks()));
        minutesDO.setOpenQuestionsJson(toJson(minutes.getOpenQuestions()));
        if (oldMinutes == null) {
            minutesMapper.insert(minutesDO);
        } else {
            minutesMapper.updateByMeetingId(tenantId, minutesDO);
        }
        actionItemMapper.deleteByMeetingId(tenantId, meetingId);
        for (TeamsMeetingMinutesDTO.ActionItem actionItem : safeList(minutes.getActionItems())) {
            actionItemMapper.insert(toActionItemDO(tenantId, meetingId, actionItem));
        }
    }

    private Long ingestTranscriptDocument(Long tenantId, Long knowledgeBaseId, AiTeamsMeetingSyncReqVO reqVO,
                                          AiMeetingDO meeting, String cleanedTranscript) {
        if (!StringUtils.hasText(cleanedTranscript)) {
            return null;
        }
        return knowledgeIngestService.upsert(MeetingKnowledgeDocumentDTO.builder()
                .tenantId(tenantId)
                .knowledgeBaseId(knowledgeBaseId)
                .directoryId(reqVO.getDirectoryId())
                .meetingId(meeting.getId())
                .sourceMeetingId(meeting.getSourceMeetingId())
                .documentType(DOCUMENT_TYPE_TRANSCRIPT)
                .title(meeting.getSubject() + " transcript")
                .content(cleanedTranscript)
                .projectCode(meeting.getProjectCode())
                .sensitivityLevel(meeting.getSensitivityLevel())
                .meetingStartTime(meeting.getStartTime())
                .build());
    }

    private Long ingestMinutesDocument(Long tenantId, Long knowledgeBaseId, AiTeamsMeetingSyncReqVO reqVO,
                                       AiMeetingDO meeting, TeamsMeetingMinutesDTO minutes) {
        if (minutes == null) {
            return null;
        }
        return knowledgeIngestService.upsert(MeetingKnowledgeDocumentDTO.builder()
                .tenantId(tenantId)
                .knowledgeBaseId(knowledgeBaseId)
                .directoryId(reqVO.getDirectoryId())
                .meetingId(meeting.getId())
                .sourceMeetingId(meeting.getSourceMeetingId())
                .documentType(DOCUMENT_TYPE_MINUTES)
                .title(meeting.getSubject() + " minutes")
                .content(toMinutesMarkdown(meeting, minutes))
                .projectCode(meeting.getProjectCode())
                .sensitivityLevel(meeting.getSensitivityLevel())
                .meetingStartTime(meeting.getStartTime())
                .build());
    }

    private AiMeetingActionItemDO toActionItemDO(Long tenantId, Long meetingId,
                                                 TeamsMeetingMinutesDTO.ActionItem actionItem) {
        AiMeetingActionItemDO actionItemDO = new AiMeetingActionItemDO();
        actionItemDO.setTenantId(tenantId);
        actionItemDO.setMeetingId(meetingId);
        actionItemDO.setTask(actionItem.getTask());
        actionItemDO.setOwner(actionItem.getOwner());
        actionItemDO.setDeadlineText(actionItem.getDeadline());
        actionItemDO.setPriority(actionItem.getPriority());
        actionItemDO.setSourceQuote(actionItem.getSourceQuote());
        return actionItemDO;
    }

    private String toMinutesMarkdown(AiMeetingDO meeting, TeamsMeetingMinutesDTO minutes) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(firstText(minutes.getTitle(), meeting.getSubject())).append("\n\n");
        appendLine(builder, "MeetingId", meeting.getId());
        appendLine(builder, "ProjectCode", meeting.getProjectCode());
        appendLine(builder, "StartTime", meeting.getStartTime());
        builder.append("\n## Summary\n").append(firstText(minutes.getSummary(), "")).append("\n");
        appendStringList(builder, "Key Points", minutes.getKeyPoints());
        appendDecisions(builder, minutes.getDecisions());
        appendActionItems(builder, minutes.getActionItems());
        appendRisks(builder, minutes.getRisks());
        appendStringList(builder, "Open Questions", minutes.getOpenQuestions());
        return builder.toString();
    }

    private void appendDecisions(StringBuilder builder, List<TeamsMeetingMinutesDTO.Decision> decisions) {
        builder.append("\n## Decisions\n");
        for (TeamsMeetingMinutesDTO.Decision item : safeList(decisions)) {
            builder.append("- ").append(firstText(item.getDecision(), "Unspecified"));
            appendInline(builder, "Context", item.getContext());
            appendInline(builder, "SourceQuote", item.getSourceQuote());
            builder.append('\n');
        }
    }

    private void appendActionItems(StringBuilder builder, List<TeamsMeetingMinutesDTO.ActionItem> actionItems) {
        builder.append("\n## Action Items\n");
        for (TeamsMeetingMinutesDTO.ActionItem item : safeList(actionItems)) {
            builder.append("- ").append(firstText(item.getTask(), "Unspecified"));
            appendInline(builder, "Owner", item.getOwner());
            appendInline(builder, "Deadline", item.getDeadline());
            appendInline(builder, "Priority", item.getPriority());
            appendInline(builder, "SourceQuote", item.getSourceQuote());
            builder.append('\n');
        }
    }

    private void appendRisks(StringBuilder builder, List<TeamsMeetingMinutesDTO.Risk> risks) {
        builder.append("\n## Risks\n");
        for (TeamsMeetingMinutesDTO.Risk item : safeList(risks)) {
            builder.append("- ").append(firstText(item.getRisk(), "Unspecified"));
            appendInline(builder, "Impact", item.getImpact());
            appendInline(builder, "Owner", item.getOwner());
            appendInline(builder, "SourceQuote", item.getSourceQuote());
            builder.append('\n');
        }
    }

    private void appendStringList(StringBuilder builder, String title, List<String> values) {
        builder.append("\n## ").append(title).append('\n');
        for (String value : safeList(values)) {
            builder.append("- ").append(value).append('\n');
        }
    }

    private void appendLine(StringBuilder builder, String label, Object value) {
        if (value != null) {
            builder.append(label).append(": ").append(value).append('\n');
        }
    }

    private void appendInline(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(" | ").append(label).append(": ").append(value);
        }
    }

    private void validateEnabled() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            throw new ServiceException(403, "Teams meeting sync is disabled");
        }
    }

    private void validateRequest(AiTeamsMeetingSyncReqVO reqVO) {
        if (reqVO.getStartDate().isAfter(reqVO.getEndDate())) {
            throw new ServiceException(400, "startDate cannot be after endDate");
        }
    }

    private void validateKnowledgeBase(Long tenantId, Long knowledgeBaseId) {
        if (knowledgeBaseMapper.selectByIdAndTenantId(knowledgeBaseId, tenantId) == null) {
            throw new ServiceException(404, "Knowledge base not found");
        }
    }

    private Long resolveKnowledgeBaseId(AiTeamsMeetingSyncReqVO reqVO) {
        Long knowledgeBaseId = reqVO.getKnowledgeBaseId() == null
                ? properties.getDefaultKnowledgeBaseId() : reqVO.getKnowledgeBaseId();
        if (knowledgeBaseId == null) {
            throw new ServiceException(400, "knowledgeBaseId is required");
        }
        return knowledgeBaseId;
    }

    private String resolveOrganizerUserId(AiTeamsMeetingSyncReqVO reqVO) {
        return requiredText(firstText(reqVO.getOrganizerUserId(), properties.getDefaultOrganizerUserId()),
                "organizerUserId is required");
    }

    private int resolveLimit(AiTeamsMeetingSyncReqVO reqVO) {
        Integer limit = reqVO.getLimit() == null ? properties.getSyncTop() : reqVO.getLimit();
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, 50));
    }

    private int resolveTranscriptMaxChars() {
        Integer maxChars = properties.getTranscriptMaxChars();
        return maxChars == null || maxChars <= 0 ? 200_000 : maxChars;
    }

    private Boolean resolveChatGptVisible(AiTeamsMeetingSyncReqVO reqVO) {
        String sensitivityLevel = resolveSensitivityLevel(reqVO);
        if ("CONFIDENTIAL".equals(sensitivityLevel) || "HR".equals(sensitivityLevel) || "FINANCE".equals(sensitivityLevel)) {
            return false;
        }
        return reqVO.getChatgptVisible() == null ? Boolean.TRUE.equals(properties.getDefaultChatgptVisible())
                : reqVO.getChatgptVisible();
    }

    private String resolveSensitivityLevel(AiTeamsMeetingSyncReqVO reqVO) {
        String value = firstText(reqVO.getSensitivityLevel(), properties.getDefaultSensitivityLevel());
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : "NORMAL";
        return switch (normalized) {
            case "NORMAL", "INTERNAL", "CONFIDENTIAL", "HR", "FINANCE" -> normalized;
            default -> "NORMAL";
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(400, message);
        }
        return value.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
