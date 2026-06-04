package cn.iocoder.yudao.module.ai.service.meeting.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MeetingKnowledgeDocumentDTO {

    private Long tenantId;

    private Long knowledgeBaseId;

    private Long directoryId;

    private Long meetingId;

    private String sourceMeetingId;

    private String documentType;

    private String title;

    private String content;

    private String projectCode;

    private String sensitivityLevel;

    private LocalDateTime meetingStartTime;

}
