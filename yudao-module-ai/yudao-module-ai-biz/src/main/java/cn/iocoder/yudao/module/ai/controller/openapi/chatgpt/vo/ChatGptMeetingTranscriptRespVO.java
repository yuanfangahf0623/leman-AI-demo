package cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGptMeetingTranscriptRespVO {

    private Long meetingId;

    private String content;

}
