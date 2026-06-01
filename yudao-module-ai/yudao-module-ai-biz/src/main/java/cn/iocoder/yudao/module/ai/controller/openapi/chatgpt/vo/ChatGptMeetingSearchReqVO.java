package cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class ChatGptMeetingSearchReqVO {

    @Size(max = 128)
    private String keyword;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @Size(max = 128)
    private String organizer;

    @Size(max = 128)
    private String projectCode;

    @Min(1)
    @Max(20)
    private Integer limit = 10;

}
