package cn.iocoder.yudao.module.ai.controller.admin.datasource.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * AI data source update request.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiDataSourceUpdateReqVO extends AiDataSourceBaseVO {

    @NotNull(message = "数据源编号不能为空")
    private Long id;

}
