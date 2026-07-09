package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.LeadMarketCountryDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Lead agent market country Mapper.
 */
@Mapper
public interface LeadMarketCountryMapper extends BaseMapper<LeadMarketCountryDO> {

    default List<LeadMarketCountryDO> selectListByCategoryId(Long tenantId, Long categoryId) {
        return selectList(Wrappers.lambdaQuery(LeadMarketCountryDO.class)
                .eq(LeadMarketCountryDO::getTenantId, tenantId)
                .eq(LeadMarketCountryDO::getCategoryId, categoryId)
                .orderByAsc(LeadMarketCountryDO::getSortOrder)
                .orderByAsc(LeadMarketCountryDO::getId));
    }

    default List<LeadMarketCountryDO> selectListByCategoryIds(Long tenantId, List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        return selectList(Wrappers.lambdaQuery(LeadMarketCountryDO.class)
                .eq(LeadMarketCountryDO::getTenantId, tenantId)
                .in(LeadMarketCountryDO::getCategoryId, categoryIds)
                .orderByAsc(LeadMarketCountryDO::getCategoryId)
                .orderByAsc(LeadMarketCountryDO::getSortOrder)
                .orderByAsc(LeadMarketCountryDO::getId));
    }

    default void deleteByCategoryId(Long tenantId, Long categoryId) {
        delete(Wrappers.lambdaQuery(LeadMarketCountryDO.class)
                .eq(LeadMarketCountryDO::getTenantId, tenantId)
                .eq(LeadMarketCountryDO::getCategoryId, categoryId));
    }

}
