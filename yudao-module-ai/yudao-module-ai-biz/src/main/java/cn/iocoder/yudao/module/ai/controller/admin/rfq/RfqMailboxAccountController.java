package cn.iocoder.yudao.module.ai.controller.admin.rfq;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.ai.controller.admin.rfq.vo.RfqMailboxAccountRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.rfq.vo.RfqMailboxAccountSaveReqVO;
import cn.iocoder.yudao.module.ai.service.rfq.RfqMailboxAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RFQ mailbox account admin controller.
 */
@RestController
@RequestMapping("/admin-api/ai/rfq-mailbox")
@Validated
@RequiredArgsConstructor
public class RfqMailboxAccountController {

    private final RfqMailboxAccountService mailboxAccountService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('ai:rfq-mailbox:query')")
    public CommonResult<List<RfqMailboxAccountRespVO>> listMailboxAccounts() {
        return CommonResult.success(mailboxAccountService.listCurrentTenantAccounts());
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('ai:rfq-mailbox:update')")
    public CommonResult<Long> saveMailboxAccount(@Valid @RequestBody RfqMailboxAccountSaveReqVO saveReqVO) {
        return CommonResult.success(mailboxAccountService.save(saveReqVO));
    }

    @GetMapping("/reveal-password")
    @PreAuthorize("@ss.hasPermission('ai:rfq-mailbox:update')")
    public CommonResult<String> revealPassword(@RequestParam("id") @NotNull(message = "id is required") Long id) {
        return CommonResult.success(mailboxAccountService.revealPassword(id));
    }

}
