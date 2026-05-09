package cn.iocoder.yudao.server.framework.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地联调用认证配置。密码等敏感值必须通过环境变量或部署配置传入。
 */
@Data
@ConfigurationProperties(prefix = "leman.auth")
public class LemanAuthProperties {

    private long accessTokenExpireSeconds = 7200;
    private long refreshTokenExpireSeconds = 604800;
    private Bootstrap bootstrap = new Bootstrap();

    @Data
    public static class Bootstrap {

        private boolean enabled = true;
        private String username = "admin";
        private String password;
        private Long tenantId = 1L;
        private Long deptId = 100L;

    }

}
