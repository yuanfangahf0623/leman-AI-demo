package cn.iocoder.yudao.module.ai.service.knowledge;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgePageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeUpdateReqVO;
import cn.iocoder.yudao.module.ai.convert.AiKnowledgeConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiKnowledgeBaseMapper;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.module.ai.enums.AiKnowledgeErrorCodeConstants.KNOWLEDGE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.ai.enums.AiKnowledgeErrorCodeConstants.KNOWLEDGE_NOT_EXISTS;

/**
 * AI knowledge service implementation.
 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeServiceImpl implements AiKnowledgeService {

    private static final Integer DEFAULT_STATUS = 0;
    private static final Integer DEFAULT_COUNT = 0;

    private final AiKnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createKnowledge(AiKnowledgeCreateReqVO createReqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        validateCodeUnique(tenantId, null, createReqVO.getCode());

        AiKnowledgeBaseDO knowledgeBase = AiKnowledgeConvert.INSTANCE.convert(createReqVO);
        knowledgeBase.setTenantId(tenantId);
        knowledgeBase.setStatus(knowledgeBase.getStatus() != null ? knowledgeBase.getStatus() : DEFAULT_STATUS);
        knowledgeBase.setDocumentCount(DEFAULT_COUNT);
        knowledgeBase.setChunkCount(DEFAULT_COUNT);
        knowledgeBaseMapper.insert(knowledgeBase);
        return knowledgeBase.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledge(AiKnowledgeUpdateReqVO updateReqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        AiKnowledgeBaseDO oldKnowledge = validateKnowledgeExists(updateReqVO.getId(), tenantId);
        validateCodeUnique(tenantId, updateReqVO.getId(), updateReqVO.getCode());

        AiKnowledgeBaseDO updateObj = AiKnowledgeConvert.INSTANCE.convert(updateReqVO);
        updateObj.setTenantId(oldKnowledge.getTenantId());
        updateObj.setDocumentCount(oldKnowledge.getDocumentCount());
        updateObj.setChunkCount(oldKnowledge.getChunkCount());
        knowledgeBaseMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledge(Long id) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        validateKnowledgeExists(id, tenantId);
        knowledgeBaseMapper.deleteById(id);
    }

    @Override
    public AiKnowledgeBaseDO getKnowledge(Long id) {
        return knowledgeBaseMapper.selectByIdAndTenantId(id, AiTenantContextHolder.getTenantId());
    }

    @Override
    public PageResult<AiKnowledgeBaseDO> getKnowledgePage(AiKnowledgePageReqVO pageReqVO) {
        return knowledgeBaseMapper.selectPage(pageReqVO, AiTenantContextHolder.getTenantId());
    }

    private AiKnowledgeBaseDO validateKnowledgeExists(Long id, Long tenantId) {
        AiKnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectByIdAndTenantId(id, tenantId);
        if (knowledgeBase == null) {
            throw new ServiceException(KNOWLEDGE_NOT_EXISTS, "知识库不存在");
        }
        return knowledgeBase;
    }

    private void validateCodeUnique(Long tenantId, Long id, String code) {
        AiKnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectByTenantIdAndCode(tenantId, code);
        if (knowledgeBase == null || knowledgeBase.getId().equals(id)) {
            return;
        }
        throw new ServiceException(KNOWLEDGE_CODE_DUPLICATE, "知识库编码在当前租户下已存在");
    }

}
