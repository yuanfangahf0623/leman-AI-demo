package cn.iocoder.yudao.module.ai.service.meeting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamsMeetingMinutesDTO {

    private String title;

    private String summary;

    @Builder.Default
    private List<String> keyPoints = new ArrayList<>();

    @Builder.Default
    private List<Decision> decisions = new ArrayList<>();

    @Builder.Default
    private List<ActionItem> actionItems = new ArrayList<>();

    @Builder.Default
    private List<Risk> risks = new ArrayList<>();

    @Builder.Default
    private List<String> openQuestions = new ArrayList<>();

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
