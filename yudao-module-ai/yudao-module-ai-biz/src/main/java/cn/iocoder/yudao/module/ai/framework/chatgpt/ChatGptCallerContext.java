package cn.iocoder.yudao.module.ai.framework.chatgpt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGptCallerContext {

    private Long tenantId;

    private String requestId;

    private String callerType;

    private String callerIdentity;

    /**
     * Reserved for OAuth integration.
     */
    private Long userId;

}
