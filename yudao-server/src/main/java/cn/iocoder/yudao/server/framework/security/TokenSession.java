package cn.iocoder.yudao.server.framework.security;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访问令牌会话信息。
 */
@Data
@Builder
public class TokenSession {

    private Long id;
    private String accessToken;
    private String refreshToken;
    private Long expiresTime;
    private Long refreshExpiresTime;
    private LoginUser loginUser;
    private LocalDateTime createTime;

    public boolean isAccessExpired(long now) {
        return expiresTime == null || expiresTime <= now;
    }

    public boolean isRefreshExpired(long now) {
        return refreshExpiresTime == null || refreshExpiresTime <= now;
    }

}
