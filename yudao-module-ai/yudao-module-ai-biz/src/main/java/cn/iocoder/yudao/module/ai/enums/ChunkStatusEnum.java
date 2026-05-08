package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档切片状态枚举。
 */
@Getter
@AllArgsConstructor
public enum ChunkStatusEnum {

    PENDING(0, "等待向量化"),
    NORMAL(10, "正常"),
    DISABLED(20, "禁用"),
    ERROR(30, "异常");

    private final Integer code;
    private final String name;

}
