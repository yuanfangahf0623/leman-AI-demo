package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档解析状态枚举。
 */
@Getter
@AllArgsConstructor
public enum DocumentParseStatusEnum {

    WAITING(0, "等待解析"),
    PARSING(10, "解析中"),
    SUCCESS(20, "解析成功"),
    FAILED(30, "解析失败");

    private final Integer code;
    private final String name;

}
