package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.LeadMarketKeywordDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Lead agent market keyword Mapper.
 */
@Mapper
public interface LeadMarketKeywordMapper extends BaseMapper<LeadMarketKeywordDO> {

    default List<LeadMarketKeywordDO> selectListByCategoryId(Long tenantId, Long categoryId) {
        return selectList(Wrappers.lambdaQuery(LeadMarketKeywordDO.class)
                .eq(LeadMarketKeywordDO::getTenantId, tenantId)
                .eq(LeadMarketKeywordDO::getCategoryId, categoryId)
                .orderByAsc(LeadMarketKeywordDO::getSortOrder)
                .orderByAsc(LeadMarketKeywordDO::getId));
    }

    default List<LeadMarketKeywordDO> selectListByCategoryIds(Long tenantId, List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        return selectList(Wrappers.lambdaQuery(LeadMarketKeywordDO.class)
                .eq(LeadMarketKeywordDO::getTenantId, tenantId)
                .in(LeadMarketKeywordDO::getCategoryId, categoryIds)
                .orderByAsc(LeadMarketKeywordDO::getCategoryId)
                .orderByAsc(LeadMarketKeywordDO::getSortOrder)
                .orderByAsc(LeadMarketKeywordDO::getId));
    }

    default void deleteByCategoryId(Long tenantId, Long categoryId) {
        delete(Wrappers.lambdaQuery(LeadMarketKeywordDO.class)
                .eq(LeadMarketKeywordDO::getTenantId, tenantId)
                .eq(LeadMarketKeywordDO::getCategoryId, categoryId));
    }

}
