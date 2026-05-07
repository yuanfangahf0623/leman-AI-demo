package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识库数据源类型枚举。
 */
@Getter
@AllArgsConstructor
public enum DataSourceTypeEnum {

    FILE("file", "文件"),
    DATABASE("database", "数据库"),
    API("api", "API"),
    WEB("web", "网页");

    private final String code;
    private final String name;

}
