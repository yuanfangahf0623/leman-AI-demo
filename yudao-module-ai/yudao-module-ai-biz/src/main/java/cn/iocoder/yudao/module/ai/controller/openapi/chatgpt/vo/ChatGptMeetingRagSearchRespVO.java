package cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGptMeetingRagSearchRespVO {

    private List<AnswerContext> answerContext;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerContext {

        private Long meetingId;

        private String documentType;

        private String subject;

        private LocalDateTime startTime;

        private String chunk;

        private Double score;

    }

}
