package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 同步模式枚举。
 */
@Getter
@AllArgsConstructor
public enum SyncModeEnum {

    MANUAL("MANUAL", "手动同步"),
    SCHEDULED("SCHEDULED", "定时同步"),
    INCREMENTAL("INCREMENTAL", "增量同步");

    private final String code;
    private final String name;

    public static boolean isValidCode(String code) {
        for (SyncModeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

}
