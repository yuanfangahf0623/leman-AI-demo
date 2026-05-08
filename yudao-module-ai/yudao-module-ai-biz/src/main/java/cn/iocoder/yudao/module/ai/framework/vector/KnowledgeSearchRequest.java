package cn.iocoder.yudao.module.ai.framework.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识库向量检索请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchRequest {

    /**
     * 租户 ID。RAG 检索必须按租户隔离。
     */
    private Long tenantId;

    /**
     * 知识库 ID。RAG 检索必须限制在指定知识库内。
     */
    private Long knowledgeBaseId;

    /**
     * 查询文本对应的 Embedding 向量。
     */
    private List<Double> queryEmbedding;

    /**
     * 返回数量。
     */
    private Integer topK;

    /**
     * 最低相似度阈值。
     */
    private Double scoreThreshold;

}
