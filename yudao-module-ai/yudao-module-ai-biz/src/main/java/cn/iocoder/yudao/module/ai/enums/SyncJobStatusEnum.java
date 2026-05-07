package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 同步任务状态枚举。
 */
@Getter
@AllArgsConstructor
public enum SyncJobStatusEnum {

    WAITING(0, "等待中"),
    RUNNING(10, "运行中"),
    SUCCESS(20, "成功"),
    FAILED(30, "失败"),
    CANCELED(40, "已取消");

    private final Integer code;
    private final String name;

}
