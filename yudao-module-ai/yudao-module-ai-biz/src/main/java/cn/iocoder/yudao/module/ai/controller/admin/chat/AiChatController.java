package cn.iocoder.yudao.module.ai.controller.admin.chat;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatCompletionReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatCompletionRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatCitationRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatConversationPinReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatConversationPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatConversationRenameReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatConversationRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatMessageRespVO;
import cn.iocoder.yudao.module.ai.convert.AiChatConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import cn.iocoder.yudao.module.ai.service.chatrecord.AiChatRecordService;
import cn.iocoder.yudao.module.ai.service.rag.RagChatResponse;
import cn.iocoder.yudao.module.ai.service.rag.RagService;
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

import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_STREAM_NOT_SUPPORTED;

/**
 * AI 问答管理端 Controller。
 *
 * <p>第一阶段只支持非流式问答，Controller 只负责权限、参数校验和统一响应封装。</p>
 */
@RestController
@RequestMapping("/admin-api/ai/chat")
@Validated
@RequiredArgsConstructor
public class AiChatController {

    private final RagService ragService;
    private final AiChatRecordService chatRecordService;

    @PostMapping("/completions")
    @PreAuthorize("@ss.hasPermission('ai:chat:completions')")
    public CommonResult<AiChatCompletionRespVO> completions(@Valid @RequestBody AiChatCompletionReqVO reqVO) {
        if (Boolean.TRUE.equals(reqVO.getStream())) {
            throw new ServiceException(RAG_STREAM_NOT_SUPPORTED, "第一阶段暂不支持流式问答");
        }
        RagChatResponse response = ragService.chat(AiChatConvert.INSTANCE.convert(reqVO));
        return CommonResult.success(AiChatConvert.INSTANCE.convert(response));
    }

    @GetMapping("/conversation/page")
    @PreAuthorize("@ss.hasPermission('ai:chat:query')")
    public CommonResult<PageResult<AiChatConversationRespVO>> getConversationPage(@Valid AiChatConversationPageReqVO pageReqVO) {
        PageResult<AiChatConversationDO> pageResult = chatRecordService.getConversationPage(pageReqVO);
        return CommonResult.success(AiChatConvert.INSTANCE.convertConversationPage(pageResult));
    }

    @PutMapping("/conversation/rename")
    @PreAuthorize("@ss.hasPermission('ai:chat:query')")
    public CommonResult<Boolean> renameConversation(@Valid @RequestBody AiChatConversationRenameReqVO reqVO) {
        chatRecordService.renameConversation(reqVO.getId(), reqVO.getTitle());
        return CommonResult.success(true);
    }

    @PutMapping("/conversation/pin")
    @PreAuthorize("@ss.hasPermission('ai:chat:query')")
    public CommonResult<Boolean> updateConversationPinned(@Valid @RequestBody AiChatConversationPinReqVO reqVO) {
        chatRecordService.updateConversationPinned(reqVO.getId(), reqVO.getPinned());
        return CommonResult.success(true);
    }

    @PutMapping("/conversation/archive")
    @PreAuthorize("@ss.hasPermission('ai:chat:query')")
    public CommonResult<Boolean> archiveConversation(@RequestParam("id")
                                                     @NotNull(message = "会话编号不能为空") Long id) {
        chatRecordService.archiveConversation(id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/conversation/delete")
    @PreAuthorize("@ss.hasPermission('ai:chat:query')")
    public CommonResult<Boolean> deleteConversation(@RequestParam("id")
                                                    @NotNull(message = "会话编号不能为空") Long id) {
        chatRecordService.deleteConversation(id);
        return CommonResult.success(true);
    }

    @GetMapping("/message/list")
    @PreAuthorize("@ss.hasPermission('ai:chat:query')")
    public CommonResult<List<AiChatMessageRespVO>> getMessageList(@RequestParam("conversationId")
                                                                  @NotNull(message = "会话编号不能为空") Long conversationId) {
        return CommonResult.success(AiChatConvert.INSTANCE.convertMessageList(
                chatRecordService.getMessageList(conversationId)));
    }

    @GetMapping("/citation/list")
    @PreAuthorize("@ss.hasPermission('ai:chat:query')")
    public CommonResult<List<AiChatCitationRespVO>> getCitationList(@RequestParam("messageId")
                                                                    @NotNull(message = "消息编号不能为空") Long messageId) {
        return CommonResult.success(AiChatConvert.INSTANCE.convertCitationList(
                chatRecordService.getCitationList(messageId)));
    }

}
