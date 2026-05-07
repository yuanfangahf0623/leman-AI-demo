package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识库可见范围枚举。
 */
@Getter
@AllArgsConstructor
public enum KnowledgeVisibilityEnum {

    PRIVATE("private", "私有"),
    TEAM("team", "团队可见"),
    PUBLIC("public", "公开");

    private final String code;
    private final String name;

}
