package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 管理后台登录响应。
 */
@Data
@Builder
public class AuthLoginRespVO {

    private Long id;
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private Integer userType;
    private String clientId;
    private Long expiresTime;

}
