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
public class ChatGptMeetingSearchRespVO {

    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        private Long meetingId;

        private String subject;

        private LocalDateTime startTime;

        private LocalDateTime endTime;

        private String organizerName;

        private String sourceType;

        private String projectCode;

        private Boolean hasTranscript;

        private Boolean hasMinutes;

        private String summary;

    }

}
