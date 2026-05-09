package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理后台账号密码登录请求。
 */
@Data
public class AuthLoginReqVO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String captchaVerification;

}
