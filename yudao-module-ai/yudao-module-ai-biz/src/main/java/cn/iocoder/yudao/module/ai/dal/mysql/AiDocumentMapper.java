package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.document.vo.AiDocumentPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 文档 Mapper。
 */
@Mapper
public interface AiDocumentMapper extends BaseMapper<AiDocumentDO> {

    default AiDocumentDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(AiDocumentDO.class)
                .eq(AiDocumentDO::getId, id)
                .eq(AiDocumentDO::getTenantId, tenantId));
    }

    default AiDocumentDO selectBySourceUri(Long tenantId, Long knowledgeBaseId, Long dataSourceId, String sourceUri) {
        return selectOne(Wrappers.lambdaQuery(AiDocumentDO.class)
                .eq(AiDocumentDO::getTenantId, tenantId)
                .eq(AiDocumentDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(AiDocumentDO::getDataSourceId, dataSourceId)
                .eq(AiDocumentDO::getSourceUri, sourceUri));
    }

    default AiDocumentDO selectBySourceUri(Long tenantId, Long knowledgeBaseId, String sourceUri) {
        return selectOne(Wrappers.lambdaQuery(AiDocumentDO.class)
                .eq(AiDocumentDO::getTenantId, tenantId)
                .eq(AiDocumentDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(AiDocumentDO::getSourceUri, sourceUri)
                .last("LIMIT 1"));
    }

    default int updateSyncDocumentByIdAndTenantId(AiDocumentDO document, Long tenantId) {
        return update(null, Wrappers.lambdaUpdate(AiDocumentDO.class)
                .set(AiDocumentDO::getDirectoryId, document.getDirectoryId())
                .set(AiDocumentDO::getDataSourceId, document.getDataSourceId())
                .set(AiDocumentDO::getTitle, document.getTitle())
                .set(AiDocumentDO::getFileName, document.getFileName())
                .set(AiDocumentDO::getFileType, document.getFileType())
                .set(AiDocumentDO::getFileSize, document.getFileSize())
                .set(AiDocumentDO::getObjectKey, document.getObjectKey())
                .set(AiDocumentDO::getSourceUri, document.getSourceUri())
                .set(AiDocumentDO::getContentHash, document.getContentHash())
                .set(AiDocumentDO::getDocumentVersion, document.getDocumentVersion())
                .set(AiDocumentDO::getParseStatus, document.getParseStatus())
                .set(AiDocumentDO::getEmbeddingStatus, document.getEmbeddingStatus())
                .set(AiDocumentDO::getChunkCount, document.getChunkCount())
                .set(AiDocumentDO::getTokenCount, document.getTokenCount())
                .set(AiDocumentDO::getErrorMessage, document.getErrorMessage())
                .eq(AiDocumentDO::getId, document.getId())
                .eq(AiDocumentDO::getTenantId, tenantId));
    }

    default int updateBasicByIdAndTenantId(AiDocumentDO document, Long tenantId) {
        return update(null, Wrappers.lambdaUpdate(AiDocumentDO.class)
                .set(AiDocumentDO::getDirectoryId, document.getDirectoryId())
                .set(AiDocumentDO::getTitle, document.getTitle())
                .set(AiDocumentDO::getDocumentVersion, document.getDocumentVersion())
                .eq(AiDocumentDO::getId, document.getId())
                .eq(AiDocumentDO::getTenantId, tenantId));
    }

    default int deleteByIdAndTenantId(Long id, Long tenantId) {
        return delete(Wrappers.lambdaQuery(AiDocumentDO.class)
                .eq(AiDocumentDO::getId, id)
                .eq(AiDocumentDO::getTenantId, tenantId));
    }

    default int updateParseStatusByIdAndTenantId(Long id, Long tenantId, Integer parseStatus, String errorMessage) {
        return update(null, Wrappers.lambdaUpdate(AiDocumentDO.class)
                .set(AiDocumentDO::getParseStatus, parseStatus)
                .set(AiDocumentDO::getErrorMessage, errorMessage)
                .eq(AiDocumentDO::getId, id)
                .eq(AiDocumentDO::getTenantId, tenantId));
    }

    default int updateEmbeddingStatusByIdAndTenantId(Long id, Long tenantId, Integer embeddingStatus,
                                                     String errorMessage) {
        return update(null, Wrappers.lambdaUpdate(AiDocumentDO.class)
                .set(AiDocumentDO::getEmbeddingStatus, embeddingStatus)
                .set(AiDocumentDO::getErrorMessage, errorMessage)
                .eq(AiDocumentDO::getId, id)
                .eq(AiDocumentDO::getTenantId, tenantId));
    }

    default int updateChunkSummaryByIdAndTenantId(Long id, Long tenantId, Integer chunkCount, Integer tokenCount) {
        return update(null, Wrappers.lambdaUpdate(AiDocumentDO.class)
                .set(AiDocumentDO::getChunkCount, chunkCount)
                .set(AiDocumentDO::getTokenCount, tokenCount)
                .eq(AiDocumentDO::getId, id)
                .eq(AiDocumentDO::getTenantId, tenantId));
    }

    default PageResult<AiDocumentDO> selectPage(AiDocumentPageReqVO reqVO, Long tenantId) {
        IPage<AiDocumentDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(AiDocumentDO.class)
                        .eq(AiDocumentDO::getTenantId, tenantId)
                .eq(reqVO.getKnowledgeBaseId() != null, AiDocumentDO::getKnowledgeBaseId,
                                reqVO.getKnowledgeBaseId())
                        .eq(reqVO.getDirectoryId() != null && reqVO.getDirectoryId() > 0,
                                AiDocumentDO::getDirectoryId, reqVO.getDirectoryId())
                        .and(reqVO.getDirectoryId() != null && reqVO.getDirectoryId() == 0,
                                query -> query.isNull(AiDocumentDO::getDirectoryId)
                                        .or().eq(AiDocumentDO::getDirectoryId, 0L))
                        .eq(reqVO.getParseStatus() != null, AiDocumentDO::getParseStatus, reqVO.getParseStatus())
                        .eq(reqVO.getEmbeddingStatus() != null, AiDocumentDO::getEmbeddingStatus,
                                reqVO.getEmbeddingStatus())
                        .like(StringUtils.isNotBlank(reqVO.getTitle()), AiDocumentDO::getTitle, reqVO.getTitle())
                        .orderByDesc(AiDocumentDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    default Long selectCountByDirectoryId(Long tenantId, Long directoryId) {
        return selectCount(Wrappers.lambdaQuery(AiDocumentDO.class)
                .eq(AiDocumentDO::getTenantId, tenantId)
                .eq(AiDocumentDO::getDirectoryId, directoryId));
    }

}
