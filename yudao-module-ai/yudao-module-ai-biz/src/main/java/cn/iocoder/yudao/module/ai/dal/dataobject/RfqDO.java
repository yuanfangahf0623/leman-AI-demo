package cn.iocoder.yudao.module.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RFQ DO.
 */
@Data
@TableName("rfq")
public class RfqDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("customer")
    private String customer;

    @TableField("product")
    private String product;

    @TableField("products_json")
    private String productsJson;

    @TableField("status")
    private String status;

    @TableField("risk_score")
    private BigDecimal riskScore;

    @TableField("confidence")
    private BigDecimal confidence;

    @TableField("message_id")
    private String messageId;

    @TableField("source_account")
    private String sourceAccount;

    @TableField("email_from")
    private String emailFrom;

    @TableField("email_subject")
    private String emailSubject;

    @TableField("received_time")
    private LocalDateTime receivedTime;

    @TableField("missing_info_json")
    private String missingInfoJson;

    @TableField("next_actions_json")
    private String nextActionsJson;

    @TableField("raw_result_json")
    private String rawResultJson;

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
