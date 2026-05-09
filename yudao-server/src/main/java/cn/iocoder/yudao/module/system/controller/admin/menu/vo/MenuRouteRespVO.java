package cn.iocoder.yudao.module.system.controller.admin.menu.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 前端动态路由菜单响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuRouteRespVO {

    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private String componentName;
    private String redirect;
    private String icon;
    private Boolean visible;
    private Boolean keepAlive;
    private Boolean alwaysShow;
    private List<MenuRouteRespVO> children;

}
