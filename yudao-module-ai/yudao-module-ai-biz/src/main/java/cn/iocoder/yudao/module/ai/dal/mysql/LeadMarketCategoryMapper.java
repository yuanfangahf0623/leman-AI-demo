package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadMarketPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadMarketCategoryDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Lead agent market category Mapper.
 */
@Mapper
public interface LeadMarketCategoryMapper extends BaseMapper<LeadMarketCategoryDO> {

    default LeadMarketCategoryDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(LeadMarketCategoryDO.class)
                .eq(LeadMarketCategoryDO::getId, id)
                .eq(LeadMarketCategoryDO::getTenantId, tenantId));
    }

    default LeadMarketCategoryDO selectByTenantIdAndCode(Long tenantId, String categoryCode) {
        return selectOne(Wrappers.lambdaQuery(LeadMarketCategoryDO.class)
                .eq(LeadMarketCategoryDO::getTenantId, tenantId)
                .eq(LeadMarketCategoryDO::getCategoryCode, categoryCode));
    }

    default List<LeadMarketCategoryDO> selectListByTenantId(Long tenantId) {
        return selectList(Wrappers.lambdaQuery(LeadMarketCategoryDO.class)
                .eq(LeadMarketCategoryDO::getTenantId, tenantId)
                .orderByDesc(LeadMarketCategoryDO::getWeight)
                .orderByAsc(LeadMarketCategoryDO::getCategoryCode));
    }

    default PageResult<LeadMarketCategoryDO> selectPage(LeadMarketPageReqVO reqVO, Long tenantId) {
        IPage<LeadMarketCategoryDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(LeadMarketCategoryDO.class)
                        .eq(LeadMarketCategoryDO::getTenantId, tenantId)
                        .like(StringUtils.isNotBlank(reqVO.getCategoryCode()),
                                LeadMarketCategoryDO::getCategoryCode, reqVO.getCategoryCode())
                        .like(StringUtils.isNotBlank(reqVO.getCategoryName()),
                                LeadMarketCategoryDO::getCategoryName, reqVO.getCategoryName())
                        .eq(reqVO.getEnabled() != null, LeadMarketCategoryDO::getEnabled, reqVO.getEnabled())
                        .orderByDesc(LeadMarketCategoryDO::getWeight)
                        .orderByAsc(LeadMarketCategoryDO::getCategoryCode));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

}
