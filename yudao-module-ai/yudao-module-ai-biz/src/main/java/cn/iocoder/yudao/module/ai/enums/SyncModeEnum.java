package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 同步模式枚举。
 */
@Getter
@AllArgsConstructor
public enum SyncModeEnum {

    FULL("full", "全量同步"),
    INCREMENTAL("incremental", "增量同步");

    private final String code;
    private final String name;

}
