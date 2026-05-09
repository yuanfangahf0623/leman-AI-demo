package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 同步任务类型枚举。
 */
@Getter
@AllArgsConstructor
public enum SyncJobTypeEnum {

    FULL("FULL", "全量同步"),
    INCREMENTAL("INCREMENTAL", "增量同步");

    private final String code;
    private final String name;

    public static boolean isValidCode(String code) {
        for (SyncJobTypeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

}
