package cn.iocoder.yudao.module.ai.controller.admin.datasource.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * AI data source page request.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiDataSourcePageReqVO extends PageParam {

    private Long knowledgeBaseId;

    private String name;

    @Pattern(regexp = "FILE|DATABASE|API|WIKI|GIT", message = "数据源类型只支持 FILE、DATABASE、API、WIKI、GIT")
    private String sourceType;

    @Pattern(regexp = "MANUAL|SCHEDULED|INCREMENTAL", message = "同步模式只支持 MANUAL、SCHEDULED、INCREMENTAL")
    private String syncMode;

    private Integer status;

}
