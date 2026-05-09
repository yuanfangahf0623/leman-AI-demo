package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.module.system.dal.dataobject.SystemUserDO;

/**
 * 管理员用户 Service。
 */
public interface SystemUserService {

    SystemUserDO getUserByUsername(Long tenantId, String username);

    void updateLoginInfo(Long id, String loginIp);

}
