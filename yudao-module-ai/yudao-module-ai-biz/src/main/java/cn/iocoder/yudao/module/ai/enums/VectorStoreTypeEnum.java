package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 向量库存储类型枚举。
 */
@Getter
@AllArgsConstructor
public enum VectorStoreTypeEnum {

    PGVECTOR("pgvector", "PostgreSQL + pgvector"),
    QDRANT("qdrant", "Qdrant");

    private final String code;
    private final String name;

}
