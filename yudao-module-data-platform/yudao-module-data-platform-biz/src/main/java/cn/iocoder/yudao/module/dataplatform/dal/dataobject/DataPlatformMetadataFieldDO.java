package cn.iocoder.yudao.module.dataplatform.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dp_metadata_field")
public class DataPlatformMetadataFieldDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("metadata_table_id")
    private Long metadataTableId;
    @TableField("source_column")
    private String sourceColumn;
    @TableField("target_column")
    private String targetColumn;
    @TableField("data_type")
    private String dataType;
    @TableField("jdbc_type")
    private Integer jdbcType;
    @TableField("column_size")
    private Integer columnSize;
    @TableField("decimal_digits")
    private Integer decimalDigits;
    private Boolean nullable;
    @TableField("primary_key")
    private Boolean primaryKey;
    @TableField("ordinal_position")
    private Integer ordinalPosition;
    @TableField("source_comment")
    private String sourceComment;
    @TableField("business_name")
    private String businessName;
    private String description;
    private String classification;
    @TableField("sensitivity_level")
    private String sensitivityLevel;
    @TableField("incremental_candidate")
    private Boolean incrementalCandidate;
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
