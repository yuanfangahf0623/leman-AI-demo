package cn.iocoder.yudao.module.ai.controller.admin.chat.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * AI 问答会话分页请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiChatConversationPageReqVO extends PageParam {

    /**
     * 管理员可按知识库筛选。
     */
    private Long knowledgeBaseId;

}
