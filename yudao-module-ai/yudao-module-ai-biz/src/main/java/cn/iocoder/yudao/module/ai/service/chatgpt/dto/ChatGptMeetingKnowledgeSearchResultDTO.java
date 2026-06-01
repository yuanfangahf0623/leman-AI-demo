package cn.iocoder.yudao.module.ai.service.chatgpt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGptMeetingKnowledgeSearchResultDTO {

    private Long meetingId;

    private Long knowledgeBaseId;

    private String documentType;

    private String chunk;

    private Double score;

}
