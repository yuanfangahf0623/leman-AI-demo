package cn.iocoder.yudao.module.ai.service.knowledge;

import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryListReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryUpdateReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeDirectoryDO;

import java.util.List;

/**
 * AI 知识库目录 Service。
 */
public interface AiKnowledgeDirectoryService {

    Long createDirectory(AiKnowledgeDirectoryCreateReqVO createReqVO);

    void updateDirectory(AiKnowledgeDirectoryUpdateReqVO updateReqVO);

    void deleteDirectory(Long id);

    AiKnowledgeDirectoryDO getDirectory(Long id);

    List<AiKnowledgeDirectoryDO> getDirectoryList(AiKnowledgeDirectoryListReqVO listReqVO);

}
