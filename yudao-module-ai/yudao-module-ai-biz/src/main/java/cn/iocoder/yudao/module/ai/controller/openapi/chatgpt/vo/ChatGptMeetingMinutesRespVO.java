package cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGptMeetingMinutesRespVO {

    private Long meetingId;

    private String title;

    private String summary;

    private List<String> keyPoints;

    private List<Decision> decisions;

    private List<ActionItem> actionItems;

    private List<Risk> risks;

    private List<String> openQuestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Decision {

        private String decision;

        private String context;

        private String sourceQuote;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionItem {

        private String task;

        private String owner;

        private String deadline;

        private String priority;

        private String sourceQuote;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Risk {

        private String risk;

        private String impact;

        private String owner;

        private String sourceQuote;

    }

}
