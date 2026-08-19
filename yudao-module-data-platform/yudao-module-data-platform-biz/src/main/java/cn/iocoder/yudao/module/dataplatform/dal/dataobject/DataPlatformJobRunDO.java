package cn.iocoder.yudao.module.dataplatform.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dp_job_run")
public class DataPlatformJobRunDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("job_id")
    private Long jobId;
    @TableField("job_name")
    private String jobName;
    @TableField("batch_id")
    private String batchId;
    @TableField("trigger_type")
    private String triggerType;
    private String status;
    @TableField("start_time")
    private LocalDateTime startTime;
    @TableField("end_time")
    private LocalDateTime endTime;
    @TableField("exit_code")
    private Integer exitCode;
    @TableField("log_path")
    private String logPath;
    @TableField("error_message")
    private String errorMessage;
    private String creator;
    @TableField("create_time")
    private LocalDateTime createTime;
}
