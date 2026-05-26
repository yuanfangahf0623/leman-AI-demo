package cn.iocoder.yudao.module.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 2hao HR attendance detail record DO.
 */
@TableName("ai_twohaohr_attendance_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTwoHaoHrAttendanceRecordDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;
    @TableField("data_source_id")
    private Long dataSourceId;
    @TableField("sync_job_id")
    private Long syncJobId;
    @TableField("provider")
    private String provider;
    @TableField("record_type")
    private String recordType;
    @TableField("external_id")
    private String externalId;
    @TableField("employee_id")
    private String employeeId;
    @TableField("employee_oa_code")
    private String employeeOaCode;
    @TableField("employee_name")
    private String employeeName;
    @TableField("department_id")
    private String departmentId;
    @TableField("department_name")
    private String departmentName;
    @TableField("attendance_date")
    private LocalDate attendanceDate;
    @TableField("start_time")
    private LocalDateTime startTime;
    @TableField("end_time")
    private LocalDateTime endTime;
    @TableField("record_status")
    private String recordStatus;
    @TableField("payload_json")
    private String payloadJson;
    @TableField("payload_hash")
    private String payloadHash;
    @TableField("record_time")
    private LocalDateTime recordTime;
    @TableField("status")
    private Integer status;
    @TableField("creator")
    private String creator;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("updater")
    private String updater;
    @TableField("update_time")
    private LocalDateTime updateTime;
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;

}
