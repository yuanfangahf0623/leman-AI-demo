package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadCustomerPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadCustomerDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Lead agent customer Mapper.
 */
@Mapper
public interface LeadCustomerMapper extends BaseMapper<LeadCustomerDO> {

    default PageResult<LeadCustomerDO> selectPage(LeadCustomerPageReqVO reqVO, Long tenantId) {
        IPage<LeadCustomerDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(LeadCustomerDO.class)
                        .eq(LeadCustomerDO::getTenantId, tenantId)
                        .like(StringUtils.isNotBlank(reqVO.getCompanyName()),
                                LeadCustomerDO::getCompanyName, reqVO.getCompanyName())
                        .like(StringUtils.isNotBlank(reqVO.getDomain()),
                                LeadCustomerDO::getDomain, reqVO.getDomain())
                        .eq(StringUtils.isNotBlank(reqVO.getCountry()), LeadCustomerDO::getCountry, reqVO.getCountry())
                        .eq(StringUtils.isNotBlank(reqVO.getMatchedCategory()),
                                LeadCustomerDO::getMatchedCategory, reqVO.getMatchedCategory())
                        .eq(reqVO.getTarget() != null, LeadCustomerDO::getTarget, reqVO.getTarget())
                        .ge(reqVO.getMinScore() != null, LeadCustomerDO::getScore, reqVO.getMinScore())
                        .isNotNull(Boolean.TRUE.equals(reqVO.getHasEmail()), LeadCustomerDO::getBestEmail)
                        .orderByDesc(LeadCustomerDO::getScore)
                        .orderByDesc(LeadCustomerDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    default List<LeadCustomerDO> selectListByTenantId(Long tenantId) {
        return selectList(Wrappers.lambdaQuery(LeadCustomerDO.class)
                .eq(LeadCustomerDO::getTenantId, tenantId)
                .orderByDesc(LeadCustomerDO::getId));
    }

    default List<LeadCustomerDO> selectList(LeadCustomerPageReqVO reqVO, Long tenantId) {
        return selectList(Wrappers.lambdaQuery(LeadCustomerDO.class)
                .eq(LeadCustomerDO::getTenantId, tenantId)
                .like(StringUtils.isNotBlank(reqVO.getCompanyName()),
                        LeadCustomerDO::getCompanyName, reqVO.getCompanyName())
                .like(StringUtils.isNotBlank(reqVO.getDomain()),
                        LeadCustomerDO::getDomain, reqVO.getDomain())
                .eq(StringUtils.isNotBlank(reqVO.getCountry()), LeadCustomerDO::getCountry, reqVO.getCountry())
                .eq(StringUtils.isNotBlank(reqVO.getMatchedCategory()),
                        LeadCustomerDO::getMatchedCategory, reqVO.getMatchedCategory())
                .eq(reqVO.getTarget() != null, LeadCustomerDO::getTarget, reqVO.getTarget())
                .ge(reqVO.getMinScore() != null, LeadCustomerDO::getScore, reqVO.getMinScore())
                .isNotNull(Boolean.TRUE.equals(reqVO.getHasEmail()), LeadCustomerDO::getBestEmail)
                .orderByDesc(LeadCustomerDO::getScore)
                .orderByDesc(LeadCustomerDO::getId));
    }

}
