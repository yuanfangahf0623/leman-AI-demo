package cn.iocoder.yudao.module.ai.service.knowledge;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgePageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeUpdateReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;

/**
 * AI knowledge service.
 */
public interface AiKnowledgeService {

    Long createKnowledge(AiKnowledgeCreateReqVO createReqVO);

    void updateKnowledge(AiKnowledgeUpdateReqVO updateReqVO);

    void deleteKnowledge(Long id);

    AiKnowledgeBaseDO getKnowledge(Long id);

    PageResult<AiKnowledgeBaseDO> getKnowledgePage(AiKnowledgePageReqVO pageReqVO);

}
