package cn.iocoder.yudao.module.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * RFQ task DO.
 */
@Data
@TableName("rfq_task")
public class RfqTaskDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("rfq_id")
    private Long rfqId;

    @TableField("task_type")
    private String taskType;

    @TableField("status")
    private String status;

    @TableField("owner")
    private String owner;

    @TableField("title")
    private String title;

    @TableField("detail")
    private String detail;

    @TableField("due_date")
    private LocalDate dueDate;

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
