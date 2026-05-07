package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentChunkDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

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

}
