package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentChunkDO;
import cn.iocoder.yudao.module.ai.enums.ChunkStatusEnum;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collections;
import java.util.List;

/**
 * AI 文档切片 Mapper。
 */
@Mapper
public interface AiDocumentChunkMapper extends BaseMapper<AiDocumentChunkDO> {

    default int deleteByDocumentIdAndTenantId(Long documentId, Long knowledgeBaseId, Long tenantId) {
        return delete(Wrappers.lambdaQuery(AiDocumentChunkDO.class)
                .eq(AiDocumentChunkDO::getDocumentId, documentId)
                .eq(AiDocumentChunkDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(AiDocumentChunkDO::getTenantId, tenantId));
    }

    default List<AiDocumentChunkDO> selectListByDocumentIdAndTenantId(Long documentId, Long knowledgeBaseId,
                                                                      Long tenantId) {
        return selectList(Wrappers.lambdaQuery(AiDocumentChunkDO.class)
                .eq(AiDocumentChunkDO::getDocumentId, documentId)
                .eq(AiDocumentChunkDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(AiDocumentChunkDO::getTenantId, tenantId)
                .orderByAsc(AiDocumentChunkDO::getChunkIndex));
    }

    default int updateEmbeddingSuccessByIdAndTenantId(Long id, Long tenantId, String vectorId,
                                                      String embeddingModel, Integer status) {
        return update(null, Wrappers.lambdaUpdate(AiDocumentChunkDO.class)
                .set(AiDocumentChunkDO::getVectorId, vectorId)
                .set(AiDocumentChunkDO::getEmbeddingModel, embeddingModel)
                .set(AiDocumentChunkDO::getStatus, status)
                .eq(AiDocumentChunkDO::getId, id)
                .eq(AiDocumentChunkDO::getTenantId, tenantId));
    }

    default int updateEmbeddingFailedByDocumentIdAndTenantId(Long documentId, Long knowledgeBaseId, Long tenantId,
                                                             Integer status) {
        return update(null, Wrappers.lambdaUpdate(AiDocumentChunkDO.class)
                .set(AiDocumentChunkDO::getVectorId, null)
                .set(AiDocumentChunkDO::getEmbeddingModel, null)
                .set(AiDocumentChunkDO::getStatus, status)
                .eq(AiDocumentChunkDO::getDocumentId, documentId)
                .eq(AiDocumentChunkDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(AiDocumentChunkDO::getTenantId, tenantId));
    }

    default List<AiDocumentChunkDO> selectLexicalCandidates(Long tenantId, Long knowledgeBaseId,
                                                            List<String> keywords, Integer limit) {
        if (tenantId == null || knowledgeBaseId == null || keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }
        int safeLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        return selectList(Wrappers.lambdaQuery(AiDocumentChunkDO.class)
                .eq(AiDocumentChunkDO::getTenantId, tenantId)
                .eq(AiDocumentChunkDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(AiDocumentChunkDO::getStatus, ChunkStatusEnum.SUCCESS.getCode())
                .and(query -> {
                    for (int i = 0; i < keywords.size(); i++) {
                        String keyword = keywords.get(i);
                        if (i == 0) {
                            query.like(AiDocumentChunkDO::getContent, keyword);
                        } else {
                            query.or().like(AiDocumentChunkDO::getContent, keyword);
                        }
                    }
                })
                .orderByAsc(AiDocumentChunkDO::getDocumentId)
                .orderByAsc(AiDocumentChunkDO::getChunkIndex)
                .last("LIMIT " + safeLimit));
    }

    default List<AiDocumentChunkDO> selectTableInventoryCandidates(Long tenantId, Long knowledgeBaseId,
                                                                   Integer limit) {
        if (tenantId == null || knowledgeBaseId == null) {
            return Collections.emptyList();
        }
        int safeLimit = limit == null || limit <= 0 ? 200 : Math.min(limit, 500);
        return selectList(Wrappers.lambdaQuery(AiDocumentChunkDO.class)
                .eq(AiDocumentChunkDO::getTenantId, tenantId)
                .eq(AiDocumentChunkDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(AiDocumentChunkDO::getStatus, ChunkStatusEnum.SUCCESS.getCode())
                .like(AiDocumentChunkDO::getContent, "表：")
                .orderByAsc(AiDocumentChunkDO::getDocumentId)
                .orderByAsc(AiDocumentChunkDO::getChunkIndex)
                .last("LIMIT " + safeLimit));
    }

}
