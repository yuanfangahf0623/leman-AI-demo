package cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class ChatGptMeetingRagSearchReqVO {

    @NotBlank
    @Size(max = 1000)
    private String query;

    @NotNull
    private Long knowledgeBaseId;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @Size(max = 128)
    private String projectCode;

    @Min(1)
    @Max(10)
    private Integer topK;

}
