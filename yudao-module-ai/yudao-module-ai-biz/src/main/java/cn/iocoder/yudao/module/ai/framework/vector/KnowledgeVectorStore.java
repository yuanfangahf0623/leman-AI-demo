package cn.iocoder.yudao.module.ai.framework.vector;

import java.util.List;

/**
 * 知识库向量存储抽象。
 *
 * <p>业务编排层只依赖该接口，不直接依赖 pgvector、Qdrant 等具体向量库 SDK。</p>
 */
public interface KnowledgeVectorStore {

    void upsert(List<KnowledgeVector> vectors);

    List<KnowledgeHit> search(KnowledgeSearchRequest request);

    void deleteByDocumentId(Long documentId);

    void deleteByKnowledgeBaseId(Long knowledgeBaseId);

    void deleteByVectorIds(List<String> vectorIds);

}
