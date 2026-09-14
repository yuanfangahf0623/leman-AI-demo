package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.module.system.dal.dataobject.SystemUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.SystemUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 管理员用户 Service 实现。
 */
@Service
@RequiredArgsConstructor
public class SystemUserServiceImpl implements SystemUserService {

    private final SystemUserMapper userMapper;
    private final HrIdentityService hrIdentityService;

    @Override
    public SystemUserDO getUserByUsername(Long tenantId, String username) {
        Long id = hrIdentityService.resolveNationalId(tenantId, username);
        if (id != null) {
            SystemUserDO user = userMapper.selectById(id);
            return user != null && tenantId.equals(user.getTenantId()) ? user : null;
        }
        SystemUserDO user = userMapper.selectByTenantIdAndUsername(tenantId, username);
        return user != null && !hrIdentityService.isImported(tenantId,user.getId()) ? user : null;
    }

    @Override
    public void updateLoginInfo(Long id, String loginIp) {
        SystemUserDO updateObj = new SystemUserDO();
        updateObj.setId(id);
        updateObj.setLoginIp(loginIp);
        updateObj.setLoginDate(LocalDateTime.now());
        updateObj.setUpdater("system");
        updateObj.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(updateObj);
    }

}
