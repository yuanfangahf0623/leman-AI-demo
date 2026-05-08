package cn.iocoder.yudao.module.ai.framework.vector.qdrant;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeSearchRequest;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVector;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVectorStore;

import java.util.List;

import static cn.iocoder.yudao.module.ai.enums.AiVectorStoreErrorCodeConstants.VECTOR_STORE_NOT_IMPLEMENTED;

/**
 * Qdrant 知识库向量存储预留实现。
 *
 * <p>TODO 后续接入 Qdrant SDK 时在该类内完成适配，业务层仍只依赖 {@link KnowledgeVectorStore}。</p>
 */
public class QdrantKnowledgeVectorStore implements KnowledgeVectorStore {

    @Override
    public void upsert(List<KnowledgeVector> vectors) {
        throwNotImplemented();
    }

    @Override
    public List<KnowledgeHit> search(KnowledgeSearchRequest request) {
        throwNotImplemented();
        return List.of();
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        throwNotImplemented();
    }

    @Override
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        throwNotImplemented();
    }

    private void throwNotImplemented() {
        throw new ServiceException(VECTOR_STORE_NOT_IMPLEMENTED, "Qdrant 向量库暂未实现");
    }

}
