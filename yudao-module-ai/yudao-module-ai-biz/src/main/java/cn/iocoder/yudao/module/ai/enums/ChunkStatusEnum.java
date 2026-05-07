package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档切片状态枚举。
 */
@Getter
@AllArgsConstructor
public enum ChunkStatusEnum {

    NORMAL(0, "正常"),
    DISABLED(1, "禁用"),
    ERROR(2, "异常");

    private final Integer code;
    private final String name;

}
