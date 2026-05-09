package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import cn.iocoder.yudao.module.system.controller.admin.menu.vo.MenuRouteRespVO;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 当前用户权限信息响应。
 */
@Data
@Builder
public class AuthPermissionInfoRespVO {

    private UserInfo user;
    private List<String> roles;
    private Set<String> permissions;
    private List<MenuRouteRespVO> menus;

    @Data
    @Builder
    public static class UserInfo {

        private Long id;
        private String username;
        private String nickname;
        private Long deptId;
        private String email;
        private String mobile;
        private Integer sex;
        private String avatar;
        private String loginIp;
        private String loginDate;

    }

}
