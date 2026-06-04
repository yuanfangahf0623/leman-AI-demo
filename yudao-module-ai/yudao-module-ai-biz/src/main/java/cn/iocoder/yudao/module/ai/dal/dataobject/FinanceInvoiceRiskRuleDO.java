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
 * Finance invoice risk rule DO.
 */
@TableName("finance_invoice_risk_rule")
@Data
public class FinanceInvoiceRiskRuleDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("rule_code")
    private String ruleCode;
    @TableField("rule_name")
    private String ruleName;
    @TableField("enabled")
    private Boolean enabled;
    @TableField("threshold_amount")
    private BigDecimal thresholdAmount;
    @TableField("currency")
    private String currency;
    @TableField("risk_level")
    private String riskLevel;
    @TableField("description")
    private String description;
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
    @TableField("tenant_id")
    private Long tenantId;

}
