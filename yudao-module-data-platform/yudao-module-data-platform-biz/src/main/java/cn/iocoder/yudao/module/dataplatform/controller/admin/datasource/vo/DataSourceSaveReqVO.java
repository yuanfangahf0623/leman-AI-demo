package cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DataSourceSaveReqVO {

    private Long id;

    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "数据源编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "数据源编码只能包含小写字母、数字和下划线")
    private String code;

    @NotBlank(message = "数据源类型不能为空")
    private String type;

    @NotBlank(message = "主机地址不能为空")
    @Size(max = 255)
    private String host;

    @NotNull(message = "端口不能为空")
    @Min(1)
    @Max(65535)
    private Integer port;

    @NotBlank(message = "数据库名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_$-]{1,128}$", message = "数据库名包含非法字符")
    private String databaseName;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 128)
    private String username;

    @Size(max = 512)
    private String password;

    @Size(max = 1000)
    private String jdbcParams;

    private Integer status;

    @Size(max = 500)
    private String remark;
}
