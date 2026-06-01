package cn.iocoder.yudao.module.ai.framework.chatgpt;

import lombok.Data;

@Data
public class ChatGptActionAuditContext {

    private String actionName;

    private Long meetingId;

    private Long knowledgeBaseId;

    private String queryText;

    private String errorCode;

}
