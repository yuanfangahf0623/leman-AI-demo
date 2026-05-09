package cn.iocoder.yudao.server.framework.security;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地联调阶段使用的内存 Token Store，后续可替换为 Redis 或 OAuth2 Token 表。
 */
@Component
public class TokenStore {

    private final LemanAuthProperties authProperties;
    private final AtomicLong tokenId = new AtomicLong(1);
    private final Map<String, TokenSession> accessTokenSessions = new ConcurrentHashMap<>();
    private final Map<String, TokenSession> refreshTokenSessions = new ConcurrentHashMap<>();

    public TokenStore(LemanAuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public TokenSession create(LoginUser loginUser) {
        long now = System.currentTimeMillis();
        TokenSession session = TokenSession.builder()
                .id(tokenId.getAndIncrement())
                .accessToken(generateToken())
                .refreshToken(generateToken())
                .expiresTime(now + authProperties.getAccessTokenExpireSeconds() * 1000)
                .refreshExpiresTime(now + authProperties.getRefreshTokenExpireSeconds() * 1000)
                .loginUser(loginUser)
                .createTime(LocalDateTime.now())
                .build();
        accessTokenSessions.put(session.getAccessToken(), session);
        refreshTokenSessions.put(session.getRefreshToken(), session);
        return session;
    }

    public TokenSession getByAccessToken(String accessToken) {
        TokenSession session = accessTokenSessions.get(accessToken);
        if (session == null || session.isAccessExpired(System.currentTimeMillis())) {
            return null;
        }
        return session;
    }

    public TokenSession refresh(String refreshToken) {
        TokenSession oldSession = refreshTokenSessions.get(refreshToken);
        long now = System.currentTimeMillis();
        if (oldSession == null || oldSession.isRefreshExpired(now)) {
            return null;
        }
        accessTokenSessions.remove(oldSession.getAccessToken());
        TokenSession newSession = TokenSession.builder()
                .id(oldSession.getId())
                .accessToken(generateToken())
                .refreshToken(oldSession.getRefreshToken())
                .expiresTime(now + authProperties.getAccessTokenExpireSeconds() * 1000)
                .refreshExpiresTime(oldSession.getRefreshExpiresTime())
                .loginUser(oldSession.getLoginUser())
                .createTime(oldSession.getCreateTime())
                .build();
        accessTokenSessions.put(newSession.getAccessToken(), newSession);
        refreshTokenSessions.put(newSession.getRefreshToken(), newSession);
        return newSession;
    }

    public void removeByAccessToken(String accessToken) {
        TokenSession session = accessTokenSessions.remove(accessToken);
        if (session != null) {
            refreshTokenSessions.remove(session.getRefreshToken());
        }
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
