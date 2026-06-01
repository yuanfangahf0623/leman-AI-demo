package cn.iocoder.yudao.module.ai.service.chatgpt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGptMeetingKnowledgeSearchRequestDTO {

    private Long tenantId;

    private String query;

    private Long knowledgeBaseId;

    private LocalDate startDate;

    private LocalDate endDate;

    private String projectCode;

    private Integer topK;

}
