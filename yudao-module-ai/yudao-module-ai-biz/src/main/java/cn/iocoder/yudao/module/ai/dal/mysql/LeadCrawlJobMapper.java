package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadCrawlJobPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadCrawlJobDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

/**
 * Lead agent crawl job Mapper.
 */
@Mapper
public interface LeadCrawlJobMapper extends BaseMapper<LeadCrawlJobDO> {

    default PageResult<LeadCrawlJobDO> selectPage(LeadCrawlJobPageReqVO reqVO, Long tenantId) {
        IPage<LeadCrawlJobDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(LeadCrawlJobDO.class)
                        .eq(LeadCrawlJobDO::getTenantId, tenantId)
                        .like(StringUtils.isNotBlank(reqVO.getRunId()), LeadCrawlJobDO::getRunId, reqVO.getRunId())
                        .eq(StringUtils.isNotBlank(reqVO.getStatus()), LeadCrawlJobDO::getStatus, reqVO.getStatus())
                        .eq(StringUtils.isNotBlank(reqVO.getCategoryCode()),
                                LeadCrawlJobDO::getCategoryCode, reqVO.getCategoryCode())
                        .orderByDesc(LeadCrawlJobDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    default LeadCrawlJobDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(LeadCrawlJobDO.class)
                .eq(LeadCrawlJobDO::getId, id)
                .eq(LeadCrawlJobDO::getTenantId, tenantId));
    }

    default void updateByIdAndTenantId(LeadCrawlJobDO updateObj, Long tenantId) {
        Long id = updateObj.getId();
        updateObj.setId(null);
        update(Wrappers.lambdaUpdate(LeadCrawlJobDO.class)
                .eq(LeadCrawlJobDO::getId, id)
                .eq(LeadCrawlJobDO::getTenantId, tenantId)
                .set(updateObj.getRunId() != null, LeadCrawlJobDO::getRunId, updateObj.getRunId())
                .set(updateObj.getCategoryCode() != null, LeadCrawlJobDO::getCategoryCode, updateObj.getCategoryCode())
                .set(updateObj.getCountry() != null, LeadCrawlJobDO::getCountry, updateObj.getCountry())
                .set(updateObj.getMaxResults() != null, LeadCrawlJobDO::getMaxResults, updateObj.getMaxResults())
                .set(updateObj.getMaxPagesPerSite() != null, LeadCrawlJobDO::getMaxPagesPerSite, updateObj.getMaxPagesPerSite())
                .set(updateObj.getCrawlTimeoutSeconds() != null, LeadCrawlJobDO::getCrawlTimeoutSeconds, updateObj.getCrawlTimeoutSeconds())
                .set(updateObj.getSearchProvider() != null, LeadCrawlJobDO::getSearchProvider, updateObj.getSearchProvider())
                .set(updateObj.getAnalysisProvider() != null, LeadCrawlJobDO::getAnalysisProvider, updateObj.getAnalysisProvider())
                .set(updateObj.getSkipSocialVerification() != null, LeadCrawlJobDO::getSkipSocialVerification, updateObj.getSkipSocialVerification())
                .set(updateObj.getEnableAiReview() != null, LeadCrawlJobDO::getEnableAiReview, updateObj.getEnableAiReview())
                .set(updateObj.getStatus() != null, LeadCrawlJobDO::getStatus, updateObj.getStatus())
                .set(updateObj.getTotalCandidates() != null, LeadCrawlJobDO::getTotalCandidates, updateObj.getTotalCandidates())
                .set(updateObj.getCrawledCount() != null, LeadCrawlJobDO::getCrawledCount, updateObj.getCrawledCount())
                .set(updateObj.getLeadCount() != null, LeadCrawlJobDO::getLeadCount, updateObj.getLeadCount())
                .set(updateObj.getExportedCount() != null, LeadCrawlJobDO::getExportedCount, updateObj.getExportedCount())
                .set(updateObj.getRejectedCount() != null, LeadCrawlJobDO::getRejectedCount, updateObj.getRejectedCount())
                .set(updateObj.getErrorMessage() != null, LeadCrawlJobDO::getErrorMessage, updateObj.getErrorMessage())
                .set(updateObj.getStartedAt() != null, LeadCrawlJobDO::getStartedAt, updateObj.getStartedAt())
                .set(updateObj.getFinishedAt() != null, LeadCrawlJobDO::getFinishedAt, updateObj.getFinishedAt()));
        updateObj.setId(id);
    }

}
