package cn.iocoder.yudao.module.ai.service.rag.fastgpt;

import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FastGptRagRequest {

    private Long tenantId;

    private Long departmentId;

    private Long knowledgeBaseId;

    private Long conversationId;

    private Long userId;

    private String question;

    private List<AiChatModelMessage> messages;

}
