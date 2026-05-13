package cn.iocoder.yudao.module.ai.service.chatrecord;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatConversationPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationKnowledgeBaseRefDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatMessageDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatCitationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatConversationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatMessageMapper;
import cn.iocoder.yudao.module.ai.enums.AiChatConversationStatusEnum;
import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_CONVERSATION_ACCESS_DENIED;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_CONVERSATION_NOT_EXISTS;

/**
 * AI 问答记录 Service 实现。
 */
@Service
@RequiredArgsConstructor
public class AiChatRecordServiceImpl implements AiChatRecordService {

    private static final Long ALL_KNOWLEDGE_BASE_ID = 0L;

    private final AiChatConversationMapper chatConversationMapper;
    private final AiChatMessageMapper chatMessageMapper;
    private final AiChatCitationMapper chatCitationMapper;

    @Override
    public PageResult<AiChatConversationDO> getConversationPage(AiChatConversationPageReqVO pageReqVO) {
        PageResult<AiChatConversationDO> pageResult = chatConversationMapper.selectPage(pageReqVO, AiUserContextHolder.getTenantId(),
                AiUserContextHolder.getDepartmentId(), AiUserContextHolder.getUserId(), AiUserContextHolder.isAdmin());
        fillDisplayKnowledgeBase(pageResult.getList(), AiUserContextHolder.getTenantId());
        return pageResult;
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

    @Override
    public void renameConversation(Long conversationId, String title) {
        validateConversationAccessible(conversationId);
        chatConversationMapper.updateTitleByIdAndTenantId(conversationId, AiUserContextHolder.getTenantId(),
                title == null ? null : title.trim());
    }

    @Override
    public void updateConversationPinned(Long conversationId, Boolean pinned) {
        validateConversationAccessible(conversationId);
        boolean pinnedValue = Boolean.TRUE.equals(pinned);
        chatConversationMapper.updatePinnedByIdAndTenantId(conversationId, AiUserContextHolder.getTenantId(),
                pinnedValue, pinnedValue ? LocalDateTime.now() : null);
    }

    @Override
    public void archiveConversation(Long conversationId) {
        validateConversationAccessible(conversationId);
        chatConversationMapper.updateStatusByIdAndTenantId(conversationId, AiUserContextHolder.getTenantId(),
                AiChatConversationStatusEnum.ARCHIVED.getStatus());
    }

    @Override
    public void deleteConversation(Long conversationId) {
        validateConversationAccessible(conversationId);
        chatConversationMapper.deleteByIdAndTenantId(conversationId, AiUserContextHolder.getTenantId());
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

    private void fillDisplayKnowledgeBase(List<AiChatConversationDO> conversations, Long tenantId) {
        if (conversations == null || conversations.isEmpty()) {
            return;
        }
        List<Long> allKnowledgeConversationIds = conversations.stream()
                .filter(conversation -> Objects.equals(ALL_KNOWLEDGE_BASE_ID, conversation.getKnowledgeBaseId()))
                .map(AiChatConversationDO::getId)
                .filter(Objects::nonNull)
                .toList();
        if (allKnowledgeConversationIds.isEmpty()) {
            return;
        }
        List<AiChatConversationKnowledgeBaseRefDO> refs = chatCitationMapper.selectKnowledgeBaseRefsByConversationIds(
                tenantId, allKnowledgeConversationIds);
        if (refs == null || refs.isEmpty()) {
            return;
        }
        Map<Long, AiChatConversationKnowledgeBaseRefDO> refMap = refs.stream()
                .collect(Collectors.toMap(AiChatConversationKnowledgeBaseRefDO::getConversationId,
                        Function.identity(), (first, ignored) -> first));
        conversations.forEach(conversation -> {
            AiChatConversationKnowledgeBaseRefDO ref = refMap.get(conversation.getId());
            if (ref == null) {
                return;
            }
            conversation.setDisplayKnowledgeBaseId(ref.getKnowledgeBaseId());
            conversation.setDisplayKnowledgeBaseName(ref.getKnowledgeBaseName());
        });
    }

}
