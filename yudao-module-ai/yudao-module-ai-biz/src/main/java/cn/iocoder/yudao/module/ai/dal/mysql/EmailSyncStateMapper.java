package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.EmailSyncStateDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * Email sync state Mapper.
 */
@Mapper
public interface EmailSyncStateMapper extends BaseMapper<EmailSyncStateDO> {

    default EmailSyncStateDO selectByAccountAndTenantId(String account, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(EmailSyncStateDO.class)
                .eq(EmailSyncStateDO::getTenantId, tenantId)
                .eq(EmailSyncStateDO::getAccount, account)
                .last("LIMIT 1"));
    }

    default int updateLastUid(Long id, Long tenantId, Long lastUid, LocalDateTime lastSyncTime) {
        return update(null, Wrappers.lambdaUpdate(EmailSyncStateDO.class)
                .set(EmailSyncStateDO::getLastUid, lastUid)
                .set(EmailSyncStateDO::getLastSyncTime, lastSyncTime)
                .eq(EmailSyncStateDO::getId, id)
                .eq(EmailSyncStateDO::getTenantId, tenantId));
    }

}
