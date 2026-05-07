package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档向量化状态枚举。
 */
@Getter
@AllArgsConstructor
public enum DocumentEmbeddingStatusEnum {

    WAITING(0, "等待向量化"),
    EMBEDDING(10, "向量化中"),
    SUCCESS(20, "向量化成功"),
    FAILED(30, "向量化失败");

    private final Integer code;
    private final String name;

}
