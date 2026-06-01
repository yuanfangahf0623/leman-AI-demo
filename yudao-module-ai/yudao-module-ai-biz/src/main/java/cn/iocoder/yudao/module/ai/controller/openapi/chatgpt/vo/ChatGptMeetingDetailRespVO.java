package cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGptMeetingDetailRespVO {

    private Long meetingId;

    private String subject;

    private String organizerName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String sourceType;

    private String projectCode;

    private String minutesStatus;

    private String transcriptStatus;

    private Long knowledgeBaseId;

}
