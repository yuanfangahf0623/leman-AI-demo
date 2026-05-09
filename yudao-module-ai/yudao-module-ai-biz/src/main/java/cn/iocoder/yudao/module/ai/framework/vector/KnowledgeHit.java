package cn.iocoder.yudao.module.ai.framework.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 知识库向量检索命中结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeHit {

    private String vectorId;

    private Long tenantId;

    private Long knowledgeBaseId;

    private Long documentId;

    private Long chunkId;

    private Integer chunkNo;

    /**
     * 文档标题，用于 RAG Prompt 来源展示。
     */
    private String documentTitle;

    private String content;

    /**
     * 相似度分数，值越大越相似。
     */
    private Double score;

    private Map<String, Object> metadata;

}
