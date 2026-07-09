package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.LeadExportRuleDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

/**
 * Lead agent export rule Mapper.
 */
@Mapper
public interface LeadExportRuleMapper extends BaseMapper<LeadExportRuleDO> {

    default LeadExportRuleDO selectByTenantId(Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(LeadExportRuleDO.class)
                .eq(LeadExportRuleDO::getTenantId, tenantId)
                .last("LIMIT 1"));
    }

}
