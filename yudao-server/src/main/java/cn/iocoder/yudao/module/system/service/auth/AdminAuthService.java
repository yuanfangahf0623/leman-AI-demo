package cn.iocoder.yudao.module.system.service.auth;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO;

/**
 * 管理后台认证 Service。
 */
public interface AdminAuthService {

    AuthLoginRespVO login(AuthLoginReqVO reqVO, String loginIp, Long tenantId);

    AuthLoginRespVO refreshToken(String refreshToken);

    void logout(String authorization);

    AuthPermissionInfoRespVO getPermissionInfo();

}
