package cn.iocoder.yudao.module.ai.service.meeting;

import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelRequest;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.meeting.dto.TeamsMeetingDTO;
import cn.iocoder.yudao.module.ai.service.meeting.dto.TeamsMeetingMinutesDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatModelTeamsMeetingMinutesGenerator implements TeamsMeetingMinutesGenerator {

    private static final int MAX_TRANSCRIPT_PROMPT_CHARS = 60_000;
    private static final int FALLBACK_SUMMARY_CHARS = 500;
    private static final String UNSPECIFIED_ZH = "\u672a\u660e\u786e";
    private static final String NO_TRANSCRIPT_SUMMARY_ZH = "\u5f53\u524d\u4f1a\u8bae\u6ca1\u6709\u53ef\u7528\u8f6c\u5f55\u5185\u5bb9\u3002";
    private static final String FALLBACK_KEY_POINT_ZH = "\u4f1a\u8bae\u8f6c\u5f55\u5df2\u540c\u6b65\uff0c\u7ed3\u6784\u5316\u7eaa\u8981\u9700\u8981\u6a21\u578b\u751f\u6210\u6216\u4eba\u5de5\u786e\u8ba4\u3002";

    private final AiChatModelService aiChatModelService;
    private final ObjectMapper objectMapper;

    @Override
    public TeamsMeetingMinutesDTO generate(TeamsMeetingDTO meeting, String cleanedTranscript) {
        if (!StringUtils.hasText(cleanedTranscript)) {
            return fallback(meeting, cleanedTranscript);
        }
        AiChatModelResponse response = aiChatModelService.chat(AiChatModelRequest.builder()
                .systemPrompt(systemPrompt())
                .userPrompt(userPrompt(meeting, cleanedTranscript))
                .temperature(0.1D)
                .build());
        String content = response == null ? null : response.getContent();
        try {
            TeamsMeetingMinutesDTO minutes = objectMapper.readValue(extractJson(content), TeamsMeetingMinutesDTO.class);
            normalize(minutes, meeting);
            return minutes;
        } catch (Exception ex) {
            log.warn("Teams meeting minutes JSON parse failed, sourceMeetingId={}, responseChars={}",
                    meeting == null ? null : meeting.getSourceMeetingId(), content == null ? 0 : content.length());
            return fallback(meeting, cleanedTranscript);
        }
    }

    private String systemPrompt() {
        String prompt = """
                You are a meeting minutes extraction service. Return JSON only.
                The JSON schema is:
                {
                  "title": "string",
                  "summary": "string",
                  "keyPoints": ["string"],
                  "decisions": [{"decision":"string","context":"string","sourceQuote":"string"}],
                  "actionItems": [{"task":"string","owner":"string","deadline":"string","priority":"low|medium|high","sourceQuote":"string"}],
                  "risks": [{"risk":"string","impact":"string","owner":"string","sourceQuote":"string"}],
                  "openQuestions": ["string"]
                }
                Use Chinese by default. Do not invent content. Use "%s" when owner or deadline is not explicit.
                """;
        return prompt.formatted(UNSPECIFIED_ZH);
    }

    private String userPrompt(TeamsMeetingDTO meeting, String cleanedTranscript) {
        return "Meeting subject: " + safe(meeting == null ? null : meeting.getSubject()) + "\n"
                + "Organizer: " + safe(meeting == null ? null : meeting.getOrganizerName()) + "\n"
                + "Start time: " + (meeting == null ? null : meeting.getStartTime()) + "\n\n"
                + "Transcript:\n" + limit(cleanedTranscript, MAX_TRANSCRIPT_PROMPT_CHARS);
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            return "{}";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        return start >= 0 && end > start ? trimmed.substring(start, end + 1) : trimmed;
    }

    private void normalize(TeamsMeetingMinutesDTO minutes, TeamsMeetingDTO meeting) {
        if (!StringUtils.hasText(minutes.getTitle())) {
            minutes.setTitle(meeting == null ? "Teams meeting minutes" : meeting.getSubject());
        }
        if (minutes.getKeyPoints() == null) {
            minutes.setKeyPoints(List.of());
        }
        if (minutes.getDecisions() == null) {
            minutes.setDecisions(List.of());
        }
        if (minutes.getActionItems() == null) {
            minutes.setActionItems(List.of());
        }
        if (minutes.getRisks() == null) {
            minutes.setRisks(List.of());
        }
        if (minutes.getOpenQuestions() == null) {
            minutes.setOpenQuestions(List.of());
        }
    }

    private TeamsMeetingMinutesDTO fallback(TeamsMeetingDTO meeting, String cleanedTranscript) {
        String title = meeting == null || !StringUtils.hasText(meeting.getSubject())
                ? "Teams meeting minutes" : meeting.getSubject();
        String summary = StringUtils.hasText(cleanedTranscript)
                ? limit(cleanedTranscript, FALLBACK_SUMMARY_CHARS) : NO_TRANSCRIPT_SUMMARY_ZH;
        return TeamsMeetingMinutesDTO.builder()
                .title(title)
                .summary(summary)
                .keyPoints(List.of(FALLBACK_KEY_POINT_ZH))
                .build();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String limit(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

}
