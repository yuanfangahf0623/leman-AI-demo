package cn.iocoder.yudao.module.system.controller.admin.menu.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 菜单新增/修改请求。
 */
@Data
public class MenuSaveReqVO {

    private Long id;

    @NotBlank(message = "菜单名称不能为空")
    private String name;

    private String permission;

    @NotNull(message = "菜单类型不能为空")
    private Integer type;

    @NotNull(message = "显示排序不能为空")
    private Integer sort;

    @NotNull(message = "上级菜单不能为空")
    private Long parentId;

    private String path;
    private String icon;
    private String component;
    private String componentName;
    private Integer status;
    private Boolean visible;
    private Boolean keepAlive;
    private Boolean alwaysShow;

}
