package cn.iocoder.yudao.module.ai.controller.admin.datasource.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 2hao HR attendance statistics response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoHaoHrAttendanceStatRespVO {

    private Long tenantId;
    private Long knowledgeBaseId;
    private Long dataSourceId;
    private String departmentId;
    private String departmentName;
    private String departmentKeyword;
    private String departmentMatchType;
    private Integer matchedDepartmentCount;
    private String employeeId;
    private String employeeName;
    private String employeeKeyword;
    private String employeeMatchType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate minAttendanceDate;
    private LocalDate maxAttendanceDate;
    private Long totalRecords;
    private Long employeeCount;
    private List<TypeStat> typeStats;
    private List<DailyStat> dailyStats;
    private List<StatusStat> statusStats;
    private List<DepartmentStat> departmentStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeStat {
        private String recordType;
        private String recordTypeName;
        private Long recordCount;
        private Long employeeCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStat {
        private LocalDate attendanceDate;
        private Long totalRecords;
        private Long employeeCount;
        private Long cardRecordCount;
        private Long cardResultCount;
        private Long leaveCount;
        private Long overtimeCount;
        private Long outingCount;
        private Long shiftCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusStat {
        private String recordType;
        private String recordTypeName;
        private String recordStatus;
        private Long recordCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentStat {
        private String departmentId;
        private String departmentName;
        private Long recordCount;
        private Long employeeCount;
        private Long cardRecordCount;
        private Long cardResultCount;
        private Long leaveCount;
        private Long overtimeCount;
        private Long outingCount;
        private Long shiftCount;
    }

}
