package cn.iocoder.yudao.framework.common.pojo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * Page query parameters.
 */
@Data
public class PageParam implements Serializable {

    @Min(value = 1, message = "页码最小值为 1")
    private Integer pageNo = 1;

    @Min(value = 1, message = "每页条数最小值为 1")
    @Max(value = 100, message = "每页条数最大值为 100")
    private Integer pageSize = 10;

}
