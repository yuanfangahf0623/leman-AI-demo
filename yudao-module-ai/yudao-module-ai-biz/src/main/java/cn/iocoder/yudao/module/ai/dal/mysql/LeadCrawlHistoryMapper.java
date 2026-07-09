package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadHistoryPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadCrawlHistoryDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Lead agent crawl history Mapper.
 */
@Mapper
public interface LeadCrawlHistoryMapper extends BaseMapper<LeadCrawlHistoryDO> {

    default PageResult<LeadCrawlHistoryDO> selectPage(LeadHistoryPageReqVO reqVO, Long tenantId) {
        IPage<LeadCrawlHistoryDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(LeadCrawlHistoryDO.class)
                        .eq(LeadCrawlHistoryDO::getTenantId, tenantId)
                        .like(StringUtils.isNotBlank(reqVO.getRunId()), LeadCrawlHistoryDO::getRunId, reqVO.getRunId())
                        .like(StringUtils.isNotBlank(reqVO.getDomain()), LeadCrawlHistoryDO::getDomain, reqVO.getDomain())
                        .eq(StringUtils.isNotBlank(reqVO.getSearchCategory()),
                                LeadCrawlHistoryDO::getSearchCategory, reqVO.getSearchCategory())
                        .eq(StringUtils.isNotBlank(reqVO.getCrawlStatus()),
                                LeadCrawlHistoryDO::getCrawlStatus, reqVO.getCrawlStatus())
                        .orderByDesc(LeadCrawlHistoryDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    default List<LeadCrawlHistoryDO> selectListByTenantId(Long tenantId) {
        return selectList(Wrappers.lambdaQuery(LeadCrawlHistoryDO.class)
                .eq(LeadCrawlHistoryDO::getTenantId, tenantId)
                .orderByDesc(LeadCrawlHistoryDO::getId));
    }

}
