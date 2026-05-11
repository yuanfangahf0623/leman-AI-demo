package cn.iocoder.yudao.module.ai.service.document;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.document.vo.AiDocumentPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.document.vo.AiDocumentUpdateReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import cn.iocoder.yudao.module.ai.framework.parser.ParsedDocument;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI document service.
 */
public interface AiDocumentService {

    Long uploadDocument(Long knowledgeBaseId, Long directoryId, MultipartFile file);

    PageResult<AiDocumentDO> getDocumentPage(AiDocumentPageReqVO pageReqVO);

    AiDocumentDO getDocument(Long id);

    void updateDocument(AiDocumentUpdateReqVO updateReqVO);

    AiDocumentPreview getDocumentPreview(Long id);

    ParsedDocument parseDocument(Long id);

    void embedDocument(Long id);

    void deleteDocument(Long id);

}
