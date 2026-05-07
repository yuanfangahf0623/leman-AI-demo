package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 同步任务触发类型枚举。
 */
@Getter
@AllArgsConstructor
public enum SyncJobTypeEnum {

    MANUAL("manual", "手动触发"),
    SCHEDULE("schedule", "定时触发"),
    API("api", "API 触发"),
    RETRY("retry", "失败重试");

    private final String code;
    private final String name;

}
