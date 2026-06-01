package cn.iocoder.yudao.module.ai.service.chatgpt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGptActionLogCreateReqDTO {

    private Long tenantId;

    private String actionName;

    private String requestId;

    private String callerType;

    private String callerIdentity;

    private Long meetingId;

    private Long knowledgeBaseId;

    private String queryText;

    private Boolean success;

    private String errorCode;

}
