package cn.iocoder.yudao.module.ai.service.document;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.document.vo.AiDocumentPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI document service.
 */
public interface AiDocumentService {

    Long uploadDocument(Long knowledgeBaseId, MultipartFile file);

    PageResult<AiDocumentDO> getDocumentPage(AiDocumentPageReqVO pageReqVO);

    AiDocumentDO getDocument(Long id);

    void deleteDocument(Long id);

}
