package cn.iocoder.yudao.module.ai.service.meeting.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TeamsMeetingDTO {

    private String sourceMeetingId;

    private String onlineMeetingId;

    private String subject;

    private String organizerName;

    private String organizerEmail;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String joinUrl;

    private String webLink;

}
