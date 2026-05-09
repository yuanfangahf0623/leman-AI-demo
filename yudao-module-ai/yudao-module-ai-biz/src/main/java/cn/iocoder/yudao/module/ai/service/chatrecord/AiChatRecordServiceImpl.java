package cn.iocoder.yudao.module.ai.service.chatrecord;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatConversationPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatMessageDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatCitationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatConversationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatMessageMapper;
import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_CONVERSATION_ACCESS_DENIED;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_CONVERSATION_NOT_EXISTS;

/**
 * AI 问答记录 Service 实现。
 */
@Service
@RequiredArgsConstructor
public class AiChatRecordServiceImpl implements AiChatRecordService {

    private final AiChatConversationMapper chatConversationMapper;
    private final AiChatMessageMapper chatMessageMapper;
    private final AiChatCitationMapper chatCitationMapper;

    @Override
    public PageResult<AiChatConversationDO> getConversationPage(AiChatConversationPageReqVO pageReqVO) {
        return chatConversationMapper.selectPage(pageReqVO, AiUserContextHolder.getTenantId(),
                AiUserContextHolder.getDepartmentId(), AiUserContextHolder.getUserId(), AiUserContextHolder.isAdmin());
    }

    @Override
    public List<AiChatMessageDO> getMessageList(Long conversationId) {
        validateConversationAccessible(conversationId);
        return chatMessageMapper.selectListByConversationId(conversationId, AiUserContextHolder.getTenantId());
    }

    @Override
    public List<AiChatCitationDO> getCitationList(Long messageId) {
        AiChatMessageDO message = chatMessageMapper.selectByIdAndTenantId(messageId, AiUserContextHolder.getTenantId());
        if (message == null) {
            throw new ServiceException(RAG_CONVERSATION_NOT_EXISTS, "消息不存在");
        }
        validateConversationAccessible(message.getConversationId());
        return chatCitationMapper.selectListByMessageId(messageId, AiUserContextHolder.getTenantId());
    }

    private AiChatConversationDO validateConversationAccessible(Long conversationId) {
        Long tenantId = AiUserContextHolder.getTenantId();
        AiChatConversationDO conversation = AiUserContextHolder.isAdmin()
                ? chatConversationMapper.selectByIdAndTenantId(conversationId, tenantId)
                : chatConversationMapper.selectByIdAndTenantIdAndDepartmentIdAndUserId(conversationId, tenantId,
                        AiUserContextHolder.getDepartmentId(), AiUserContextHolder.getUserId());
        if (conversation == null) {
            throw new ServiceException(RAG_CONVERSATION_NOT_EXISTS, "会话不存在");
        }
        if (!AiUserContextHolder.isAdmin() && !AiUserContextHolder.getUserId().equals(conversation.getUserId())) {
            throw new ServiceException(RAG_CONVERSATION_ACCESS_DENIED, "无权查看该会话");
        }
        return conversation;
    }

}
