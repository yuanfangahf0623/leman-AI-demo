package cn.iocoder.yudao.module.ai.controller.admin.meeting.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class AiTeamsMeetingSyncReqVO {

    @Size(max = 256)
    private String organizerUserId;

    @NotNull
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private Long knowledgeBaseId;

    private Long directoryId;

    @Size(max = 128)
    private String projectCode;

    @Size(max = 32)
    private String sensitivityLevel;

    private Boolean chatgptVisible;

    @Min(1)
    @Max(50)
    private Integer limit;

}
