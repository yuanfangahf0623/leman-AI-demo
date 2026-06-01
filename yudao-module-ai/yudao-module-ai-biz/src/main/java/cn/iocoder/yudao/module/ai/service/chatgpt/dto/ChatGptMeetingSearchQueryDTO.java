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
public class ChatGptMeetingSearchQueryDTO {

    private String keyword;

    private LocalDate startDate;

    private LocalDate endDate;

    private String organizer;

    private String projectCode;

}
