package cn.iocoder.yudao.module.ai.controller.admin.datasource.vo;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 2hao HR attendance statistics request.
 */
@Data
public class TwoHaoHrAttendanceStatReqVO {

    private Long knowledgeBaseId;

    private Long dataSourceId;

    private String departmentId;

    private String departmentName;

    /**
     * Free-text department hint used by RAG, for example a user's whole question.
     */
    private String departmentKeyword;

    private String employeeId;

    private String employeeName;

    /**
     * Free-text employee hint used by RAG, for example a user's whole question.
     */
    private String employeeKeyword;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @AssertTrue(message = "knowledgeBaseId 和 dataSourceId 不能同时为空")
    public boolean isScopeValid() {
        return knowledgeBaseId != null || dataSourceId != null;
    }

}
