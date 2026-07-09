package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.RfqDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

/**
 * RFQ Mapper.
 */
@Mapper
public interface RfqMapper extends BaseMapper<RfqDO> {

    default RfqDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(RfqDO.class)
                .eq(RfqDO::getId, id)
                .eq(RfqDO::getTenantId, tenantId));
    }

    default RfqDO selectByMessageId(String messageId) {
        return selectOne(Wrappers.lambdaQuery(RfqDO.class)
                .eq(RfqDO::getMessageId, messageId)
                .last("LIMIT 1"));
    }

    default int updateStatusByIdAndTenantId(Long id, Long tenantId, String status) {
        return update(null, Wrappers.lambdaUpdate(RfqDO.class)
                .set(RfqDO::getStatus, status)
                .eq(RfqDO::getId, id)
                .eq(RfqDO::getTenantId, tenantId));
    }

}
