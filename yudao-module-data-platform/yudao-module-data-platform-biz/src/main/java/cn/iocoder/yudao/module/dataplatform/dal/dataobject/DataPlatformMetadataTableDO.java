package cn.iocoder.yudao.module.dataplatform.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dp_metadata_table")
public class DataPlatformMetadataTableDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("data_source_id")
    private Long dataSourceId;
    @TableField("source_schema")
    private String sourceSchema;
    @TableField("source_table")
    private String sourceTable;
    @TableField("business_name")
    private String businessName;
    @TableField("business_domain")
    private String businessDomain;
    private String description;
    @TableField("target_database")
    private String targetDatabase;
    @TableField("target_table")
    private String targetTable;
    @TableField("field_count")
    private Integer fieldCount;
    @TableField("commented_field_count")
    private Integer commentedFieldCount;
    @TableField("definition_status")
    private String definitionStatus;
    private Integer status;
    @TableField("last_scan_time")
    private LocalDateTime lastScanTime;
    private String creator;
    @TableField("create_time")
    private LocalDateTime createTime;
    private String updater;
    @TableField("update_time")
    private LocalDateTime updateTime;
    @TableLogic
    private Boolean deleted;
}
