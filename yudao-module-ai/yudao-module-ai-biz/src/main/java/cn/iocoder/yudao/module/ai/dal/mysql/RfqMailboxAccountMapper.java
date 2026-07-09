package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.RfqMailboxAccountDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * RFQ mailbox account Mapper.
 */
@Mapper
public interface RfqMailboxAccountMapper extends BaseMapper<RfqMailboxAccountDO> {

    default RfqMailboxAccountDO selectByAccountAndTenantId(String account, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(RfqMailboxAccountDO.class)
                .eq(RfqMailboxAccountDO::getTenantId, tenantId)
                .eq(RfqMailboxAccountDO::getAccount, account)
                .last("LIMIT 1"));
    }

    default List<RfqMailboxAccountDO> selectEnabledList() {
        return selectList(Wrappers.lambdaQuery(RfqMailboxAccountDO.class)
                .eq(RfqMailboxAccountDO::getEnabled, true)
                .orderByAsc(RfqMailboxAccountDO::getId));
    }

    default List<RfqMailboxAccountDO> selectListByTenantId(Long tenantId) {
        return selectList(Wrappers.lambdaQuery(RfqMailboxAccountDO.class)
                .eq(RfqMailboxAccountDO::getTenantId, tenantId)
                .orderByAsc(RfqMailboxAccountDO::getId));
    }

    default RfqMailboxAccountDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(RfqMailboxAccountDO.class)
                .eq(RfqMailboxAccountDO::getId, id)
                .eq(RfqMailboxAccountDO::getTenantId, tenantId));
    }

    default int updateByAccountAndTenantId(RfqMailboxAccountDO accountDO) {
        Long tenantId = accountDO.getTenantId();
        String account = accountDO.getAccount();
        accountDO.setId(null);
        accountDO.setTenantId(null);
        accountDO.setAccount(null);
        return update(accountDO, Wrappers.lambdaQuery(RfqMailboxAccountDO.class)
                .eq(RfqMailboxAccountDO::getTenantId, tenantId)
                .eq(RfqMailboxAccountDO::getAccount, account));
    }

}
