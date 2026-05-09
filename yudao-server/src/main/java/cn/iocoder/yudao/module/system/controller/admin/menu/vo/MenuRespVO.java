package cn.iocoder.yudao.module.system.controller.admin.menu.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单管理响应。
 */
@Data
public class MenuRespVO {

    private Long id;
    private String name;
    private String permission;
    private Integer type;
    private Integer sort;
    private Long parentId;
    private String path;
    private String icon;
    private String component;
    private String componentName;
    private Integer status;
    private Boolean visible;
    private Boolean keepAlive;
    private Boolean alwaysShow;
    private LocalDateTime createTime;

}
