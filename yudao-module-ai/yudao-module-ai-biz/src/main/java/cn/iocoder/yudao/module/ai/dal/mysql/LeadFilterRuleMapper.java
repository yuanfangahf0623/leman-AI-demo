package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.LeadFilterRuleDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Lead agent filter rule Mapper.
 */
@Mapper
public interface LeadFilterRuleMapper extends BaseMapper<LeadFilterRuleDO> {

    default List<LeadFilterRuleDO> selectListByTenantId(Long tenantId) {
        return selectList(Wrappers.lambdaQuery(LeadFilterRuleDO.class)
                .eq(LeadFilterRuleDO::getTenantId, tenantId)
                .eq(LeadFilterRuleDO::getEnabled, true)
                .orderByAsc(LeadFilterRuleDO::getRuleType)
                .orderByAsc(LeadFilterRuleDO::getSortOrder)
                .orderByAsc(LeadFilterRuleDO::getId));
    }

    default List<LeadFilterRuleDO> selectListByType(Long tenantId, String ruleType) {
        return selectList(Wrappers.lambdaQuery(LeadFilterRuleDO.class)
                .eq(LeadFilterRuleDO::getTenantId, tenantId)
                .eq(LeadFilterRuleDO::getRuleType, ruleType)
                .eq(LeadFilterRuleDO::getEnabled, true)
                .orderByAsc(LeadFilterRuleDO::getSortOrder)
                .orderByAsc(LeadFilterRuleDO::getId));
    }

    default void deleteByTenantId(Long tenantId) {
        delete(Wrappers.lambdaQuery(LeadFilterRuleDO.class)
                .eq(LeadFilterRuleDO::getTenantId, tenantId));
    }

}
