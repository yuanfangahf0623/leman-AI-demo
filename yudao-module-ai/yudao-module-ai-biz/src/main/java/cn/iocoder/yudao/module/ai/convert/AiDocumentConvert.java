package cn.iocoder.yudao.module.ai.convert;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.document.vo.AiDocumentRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;

import java.util.List;

/**
 * AI 文档 Convert。
 */
public class AiDocumentConvert {

    public static final AiDocumentConvert INSTANCE = new AiDocumentConvert();

    public AiDocumentRespVO convert(AiDocumentDO bean) {
        if (bean == null) {
            return null;
        }
        AiDocumentRespVO result = new AiDocumentRespVO();
        result.setId(bean.getId());
        result.setKnowledgeBaseId(bean.getKnowledgeBaseId());
        result.setDirectoryId(bean.getDirectoryId());
        result.setDataSourceId(bean.getDataSourceId());
        result.setDocumentVersion(bean.getDocumentVersion());
        result.setTitle(bean.getTitle());
        result.setFileName(bean.getFileName());
        result.setFileType(bean.getFileType());
        result.setFileSize(bean.getFileSize());
        result.setObjectKey(bean.getObjectKey());
        result.setSourceUri(bean.getSourceUri());
        result.setContentHash(bean.getContentHash());
        result.setParseStatus(bean.getParseStatus());
        result.setEmbeddingStatus(bean.getEmbeddingStatus());
        result.setChunkCount(bean.getChunkCount());
        result.setTokenCount(bean.getTokenCount());
        result.setErrorMessage(bean.getErrorMessage());
        result.setCreateTime(bean.getCreateTime());
        result.setUpdateTime(bean.getUpdateTime());
        return result;
    }

    public PageResult<AiDocumentRespVO> convertPage(PageResult<AiDocumentDO> page) {
        if (page == null) {
            return null;
        }
        List<AiDocumentRespVO> list = page.getList().stream()
                .map(this::convert)
                .toList();
        return new PageResult<>(list, page.getTotal());
    }

}
