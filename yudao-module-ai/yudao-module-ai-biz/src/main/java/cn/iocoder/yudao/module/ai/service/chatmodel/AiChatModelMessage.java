package cn.iocoder.yudao.module.ai.service.chatmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天模型消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatModelMessage {

    /**
     * 消息角色，例如 system、user、assistant。
     */
    private String role;

    /**
     * 消息内容。
     */
    private String content;

}
