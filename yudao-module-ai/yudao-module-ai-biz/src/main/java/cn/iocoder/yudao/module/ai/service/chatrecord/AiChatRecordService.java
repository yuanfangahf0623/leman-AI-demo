package cn.iocoder.yudao.module.ai.service.chatrecord;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatConversationPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatMessageDO;

import java.util.List;

/**
 * AI 问答记录 Service。
 */
public interface AiChatRecordService {

    PageResult<AiChatConversationDO> getConversationPage(AiChatConversationPageReqVO pageReqVO);

    List<AiChatMessageDO> getMessageList(Long conversationId);

    List<AiChatCitationDO> getCitationList(Long messageId);

}
