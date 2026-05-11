package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 问答会话状态。
 */
@Getter
@AllArgsConstructor
public enum AiChatConversationStatusEnum {

    NORMAL(0, "正常"),
    ARCHIVED(20, "已归档");

    private final Integer status;
    private final String name;

}
