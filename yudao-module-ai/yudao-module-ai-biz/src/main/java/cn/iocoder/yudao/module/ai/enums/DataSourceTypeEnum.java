package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识库数据源类型枚举。
 */
@Getter
@AllArgsConstructor
public enum DataSourceTypeEnum {

    FILE("FILE", "文件"),
    DATABASE("DATABASE", "数据库"),
    API("API", "API"),
    WIKI("WIKI", "Wiki"),
    GIT("GIT", "Git");

    private final String code;
    private final String name;

    public static boolean isValidCode(String code) {
        for (DataSourceTypeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

}
