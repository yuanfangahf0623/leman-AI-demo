package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Lead market save request.
 */
@Data
public class LeadMarketSaveReqVO {

    private Long id;

    @NotBlank(message = "分类 ID 不能为空")
    @Pattern(regexp = "^[a-z0-9_]+$", message = "分类 ID 只能包含小写字母、数字和下划线")
    @Size(max = 64, message = "分类 ID 不能超过 64 个字符")
    private String categoryCode;

    @Size(max = 128, message = "分类名称不能超过 128 个字符")
    private String categoryName;

    @Min(value = 1, message = "权重最小值为 1")
    @Max(value = 1000, message = "权重最大值为 1000")
    private Integer weight = 10;

    private Boolean enabled = true;

    @NotEmpty(message = "至少需要一个国家")
    private List<@NotBlank(message = "国家不能为空") @Size(max = 128, message = "国家不能超过 128 个字符") String> countries;

    @NotEmpty(message = "至少需要一个搜索关键词")
    private List<@NotBlank(message = "搜索关键词不能为空") @Size(max = 255, message = "搜索关键词不能超过 255 个字符") String> keywords;

}
