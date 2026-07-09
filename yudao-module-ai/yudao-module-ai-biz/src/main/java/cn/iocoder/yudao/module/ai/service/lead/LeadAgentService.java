package cn.iocoder.yudao.module.ai.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadAgentDashboardRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadCrawlJobPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadCrawlJobRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadCustomerPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadCustomerRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadExportRuleRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadExportRuleSaveReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadFilterRuleRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadFilterRuleSaveReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadHistoryPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadHistoryRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadMarketPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadMarketRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadMarketSaveReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadRunCreateReqVO;

import java.util.List;

/**
 * Lead agent Service.
 */
public interface LeadAgentService {

    PageResult<LeadMarketRespVO> getMarketPage(LeadMarketPageReqVO pageReqVO);

    List<LeadMarketRespVO> getMarketList();

    LeadMarketRespVO getMarket(Long id);

    Long createMarket(LeadMarketSaveReqVO createReqVO);

    void updateMarket(LeadMarketSaveReqVO updateReqVO);

    void deleteMarket(Long id);

    LeadFilterRuleRespVO getFilterRules();

    void updateFilterRules(LeadFilterRuleSaveReqVO saveReqVO);

    LeadExportRuleRespVO getExportRules();

    void updateExportRules(LeadExportRuleSaveReqVO saveReqVO);

    PageResult<LeadCustomerRespVO> getCustomerPage(LeadCustomerPageReqVO pageReqVO);

    PageResult<LeadHistoryRespVO> getHistoryPage(LeadHistoryPageReqVO pageReqVO);

    Long startRun(LeadRunCreateReqVO createReqVO);

    PageResult<LeadCrawlJobRespVO> getJobPage(LeadCrawlJobPageReqVO pageReqVO);

    LeadCrawlJobRespVO getJob(Long id);

    byte[] exportCustomers(LeadCustomerPageReqVO pageReqVO);

    LeadAgentDashboardRespVO getDashboard();

}
