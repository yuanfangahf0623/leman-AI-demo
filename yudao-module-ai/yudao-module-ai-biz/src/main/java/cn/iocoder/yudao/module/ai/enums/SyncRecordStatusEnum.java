package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 同步记录处理状态枚举。
 */
@Getter
@AllArgsConstructor
public enum SyncRecordStatusEnum {

    SUCCESS(20, "成功"),
    FAILED(30, "失败");

    private final Integer code;
    private final String name;

}
