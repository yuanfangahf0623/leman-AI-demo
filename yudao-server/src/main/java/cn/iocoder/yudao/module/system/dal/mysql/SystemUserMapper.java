package cn.iocoder.yudao.module.system.dal.mysql;

import cn.iocoder.yudao.module.system.dal.dataobject.SystemUserDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员用户 Mapper。
 */
@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUserDO> {

    default SystemUserDO selectByTenantIdAndUsername(Long tenantId, String username) {
        return selectOne(Wrappers.lambdaQuery(SystemUserDO.class)
                .eq(SystemUserDO::getTenantId, tenantId)
                .eq(SystemUserDO::getUsername, username)
                .eq(SystemUserDO::getDeleted, false));
    }

}
