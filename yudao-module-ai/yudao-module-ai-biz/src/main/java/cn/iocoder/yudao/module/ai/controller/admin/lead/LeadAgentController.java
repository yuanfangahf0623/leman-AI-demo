package cn.iocoder.yudao.module.ai.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadAgentDashboardRespVO;
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
import cn.iocoder.yudao.module.ai.service.lead.LeadAgentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lead agent admin Controller.
 */
@RestController
@RequestMapping("/admin-api/ai/lead-agent")
@Validated
@RequiredArgsConstructor
public class LeadAgentController {

    private final LeadAgentService leadAgentService;

    @GetMapping("/dashboard")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:query')")
    public CommonResult<LeadAgentDashboardRespVO> getDashboard() {
        return CommonResult.success(leadAgentService.getDashboard());
    }

    @GetMapping("/market/page")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:query')")
    public CommonResult<PageResult<LeadMarketRespVO>> getMarketPage(@Valid LeadMarketPageReqVO pageReqVO) {
        return CommonResult.success(leadAgentService.getMarketPage(pageReqVO));
    }

    @GetMapping("/market/list")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:query')")
    public CommonResult<List<LeadMarketRespVO>> getMarketList() {
        return CommonResult.success(leadAgentService.getMarketList());
    }

    @GetMapping("/market/get")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:query')")
    public CommonResult<LeadMarketRespVO> getMarket(@RequestParam("id") @NotNull(message = "市场分类编号不能为空") Long id) {
        return CommonResult.success(leadAgentService.getMarket(id));
    }

    @PostMapping("/market/create")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:update')")
    public CommonResult<Long> createMarket(@Valid @RequestBody LeadMarketSaveReqVO createReqVO) {
        return CommonResult.success(leadAgentService.createMarket(createReqVO));
    }

    @PutMapping("/market/update")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:update')")
    public CommonResult<Boolean> updateMarket(@Valid @RequestBody LeadMarketSaveReqVO updateReqVO) {
        leadAgentService.updateMarket(updateReqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/market/delete")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:update')")
    public CommonResult<Boolean> deleteMarket(@RequestParam("id") @NotNull(message = "市场分类编号不能为空") Long id) {
        leadAgentService.deleteMarket(id);
        return CommonResult.success(true);
    }

    @GetMapping("/filter-rules")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:query')")
    public CommonResult<LeadFilterRuleRespVO> getFilterRules() {
        return CommonResult.success(leadAgentService.getFilterRules());
    }

    @PutMapping("/filter-rules")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:update')")
    public CommonResult<Boolean> updateFilterRules(@Valid @RequestBody LeadFilterRuleSaveReqVO saveReqVO) {
        leadAgentService.updateFilterRules(saveReqVO);
        return CommonResult.success(true);
    }

    @GetMapping("/export-rules")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:query')")
    public CommonResult<LeadExportRuleRespVO> getExportRules() {
        return CommonResult.success(leadAgentService.getExportRules());
    }

    @PutMapping("/export-rules")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:update')")
    public CommonResult<Boolean> updateExportRules(@Valid @RequestBody LeadExportRuleSaveReqVO saveReqVO) {
        leadAgentService.updateExportRules(saveReqVO);
        return CommonResult.success(true);
    }

    @GetMapping("/customer/page")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:query')")
    public CommonResult<PageResult<LeadCustomerRespVO>> getCustomerPage(@Valid LeadCustomerPageReqVO pageReqVO) {
        return CommonResult.success(leadAgentService.getCustomerPage(pageReqVO));
    }

    @GetMapping("/history/page")
    @PreAuthorize("@ss.hasPermission('ai:lead-agent:query')")
    public CommonResult<PageResult<LeadHistoryRespVO>> getHistoryPage(@Valid LeadHistoryPageReqVO pageReqVO) {
        return CommonResult.success(leadAgentService.getHistoryPage(pageReqVO));
    }

}
