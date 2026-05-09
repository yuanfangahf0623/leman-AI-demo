package cn.iocoder.yudao.server.framework.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 当前登录用户上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Long id;
    private String username;
    private String nickname;
    private Long tenantId;
    private Long deptId;
    private Boolean admin;
    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    public boolean isAdmin() {
        return Boolean.TRUE.equals(admin);
    }

}
