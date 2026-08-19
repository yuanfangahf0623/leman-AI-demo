package cn.iocoder.yudao.module.dataplatform.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dp_sync_job")
public class DataPlatformSyncJobDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    @TableField("source_data_source_id")
    private Long sourceDataSourceId;
    @TableField("source_sql")
    private String sourceSql;
    @TableField("target_data_source_id")
    private Long targetDataSourceId;
    @TableField("target_database")
    private String targetDatabase;
    @TableField("target_table")
    private String targetTable;
    @TableField("sink_sql")
    private String sinkSql;
    @TableField("sync_mode")
    private String syncMode;
    @TableField("watermark_column")
    private String watermarkColumn;
    @TableField("watermark_value")
    private String watermarkValue;
    private Integer parallelism;
    private Integer status;
    private String remark;
    private String creator;
    @TableField("create_time")
    private LocalDateTime createTime;
    private String updater;
    @TableField("update_time")
    private LocalDateTime updateTime;
    @TableLogic
    private Boolean deleted;
}
