package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 问答消息角色枚举。
 */
@Getter
@AllArgsConstructor
public enum ChatMessageRoleEnum {

    SYSTEM("system", "系统"),
    USER("user", "用户"),
    ASSISTANT("assistant", "助手"),
    TOOL("tool", "工具");

    private final String code;
    private final String name;

}
