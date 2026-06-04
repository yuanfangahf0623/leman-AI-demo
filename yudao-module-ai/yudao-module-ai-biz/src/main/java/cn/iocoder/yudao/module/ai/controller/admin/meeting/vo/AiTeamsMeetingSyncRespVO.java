package cn.iocoder.yudao.module.ai.controller.admin.meeting.vo;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class AiTeamsMeetingSyncRespVO {

    private Integer scannedCount;

    private Integer syncedCount;

    private Integer skippedCount;

    private Integer failedCount;

    @Builder.Default
    private List<Item> items = new ArrayList<>();

    @Data
    @Builder
    public static class Item {

        private String sourceMeetingId;

        private Long meetingId;

        private String subject;

        private String action;

        private String transcriptStatus;

        private String minutesStatus;

        private Long transcriptDocumentId;

        private Long minutesDocumentId;

        private String errorCode;

    }

}
