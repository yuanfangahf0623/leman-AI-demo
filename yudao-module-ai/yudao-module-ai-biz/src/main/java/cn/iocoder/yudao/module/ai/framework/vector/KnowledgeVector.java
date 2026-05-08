package cn.iocoder.yudao.module.ai.framework.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 知识库向量写入对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeVector {

    /**
     * 向量库侧 ID。为空时 Mock 实现会使用 chunkId 作为幂等键。
     */
    private String vectorId;

    /**
     * 租户 ID，用于向量数据隔离。
     */
    private Long tenantId;

    /**
     * 知识库 ID，用于 RAG 检索过滤。
     */
    private Long knowledgeBaseId;

    /**
     * 文档 ID。
     */
    private Long documentId;

    /**
     * 文档切片 ID。
     */
    private Long chunkId;

    /**
     * 切片序号。
     */
    private Integer chunkNo;

    /**
     * 切片文本。
     */
    private String content;

    /**
     * Embedding 向量。
     */
    private List<Double> embedding;

    /**
     * 业务元数据，例如标题、文件名、页码等。
     */
    private Map<String, Object> metadata;

}
