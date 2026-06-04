package cn.iocoder.yudao.module.ai.service.meeting.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeamsTranscriptDTO {

    private String transcriptId;

    private String content;

    private String contentType;

}
