package cn.iocoder.yudao.module.ai.service.knowledge;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgePageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeUpdateReqVO;
import cn.iocoder.yudao.module.ai.convert.AiKnowledgeConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiKnowledgeBaseMapper;
import cn.iocoder.yudao.module.ai.enums.KnowledgeVisibilityEnum;
import cn.iocoder.yudao.module.ai.enums.VectorStoreTypeEnum;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.module.ai.enums.AiKnowledgeErrorCodeConstants.KNOWLEDGE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.ai.enums.AiKnowledgeErrorCodeConstants.KNOWLEDGE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiKnowledgeErrorCodeConstants.KNOWLEDGE_VECTOR_STORE_TYPE_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiKnowledgeErrorCodeConstants.KNOWLEDGE_VISIBILITY_INVALID;

/**
 * AI 知识库 Service 实现。
 *
 * <p>负责知识库 CRUD 的业务编排，包括租户边界、code 唯一性和逻辑删除入口。</p>
 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeServiceImpl implements AiKnowledgeService {

    private static final Integer DEFAULT_STATUS = 0;
    private static final Integer DEFAULT_COUNT = 0;
    private static final String DEFAULT_DEPARTMENT_IDS = "*";
    private static final String DEFAULT_VISIBILITY = KnowledgeVisibilityEnum.PUBLIC.getCode();
    private static final Double DEFAULT_SCORE_THRESHOLD = 0.7D;

    private final AiKnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createKnowledge(AiKnowledgeCreateReqVO createReqVO) {
        // 当前租户是知识库唯一性和数据隔离的边界。
        Long tenantId = AiTenantContextHolder.getTenantId();
        // 同一租户内知识库 code 不允许重复。
        validateCodeUnique(tenantId, null, createReqVO.getCode());
        validateVisibility(createReqVO.getVisibility());
        validateVectorStoreType(createReqVO.getVectorStoreType());

        AiKnowledgeBaseDO knowledgeBase = AiKnowledgeConvert.INSTANCE.convert(createReqVO);
        knowledgeBase.setTenantId(tenantId);
        // 新建知识库时初始化状态和统计字段，文档/切片数量后续由文档流程维护。
        knowledgeBase.setStatus(knowledgeBase.getStatus() != null ? knowledgeBase.getStatus() : DEFAULT_STATUS);
        knowledgeBase.setVisibility(normalizeVisibility(knowledgeBase.getVisibility()));
        knowledgeBase.setDepartmentIds(normalizeDepartmentIds(knowledgeBase.getDepartmentIds()));
        knowledgeBase.setScoreThreshold(normalizeScoreThreshold(knowledgeBase.getScoreThreshold()));
        knowledgeBase.setDocumentCount(DEFAULT_COUNT);
        knowledgeBase.setChunkCount(DEFAULT_COUNT);
        knowledgeBaseMapper.insert(knowledgeBase);
        return knowledgeBase.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledge(AiKnowledgeUpdateReqVO updateReqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        // 更新前先确认当前租户下记录存在，避免跨租户更新。
        AiKnowledgeBaseDO oldKnowledge = validateKnowledgeExists(updateReqVO.getId(), tenantId);
        // 允许保持自身 code 不变，但不允许改成同租户已有 code。
        validateCodeUnique(tenantId, updateReqVO.getId(), updateReqVO.getCode());
        validateVisibility(updateReqVO.getVisibility());
        validateVectorStoreType(updateReqVO.getVectorStoreType());

        AiKnowledgeBaseDO updateObj = AiKnowledgeConvert.INSTANCE.convert(updateReqVO);
        updateObj.setTenantId(oldKnowledge.getTenantId());
        updateObj.setVisibility(normalizeVisibility(updateObj.getVisibility() != null
                ? updateObj.getVisibility() : oldKnowledge.getVisibility()));
        updateObj.setDepartmentIds(normalizeDepartmentIds(updateObj.getDepartmentIds() != null
                ? updateObj.getDepartmentIds() : oldKnowledge.getDepartmentIds()));
        updateObj.setScoreThreshold(normalizeScoreThreshold(updateObj.getScoreThreshold() != null
                ? updateObj.getScoreThreshold() : oldKnowledge.getScoreThreshold()));
        // 统计字段不由知识库基础信息更新接口直接修改。
        updateObj.setDocumentCount(oldKnowledge.getDocumentCount());
        updateObj.setChunkCount(oldKnowledge.getChunkCount());
        knowledgeBaseMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledge(Long id) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        validateKnowledgeExists(id, tenantId);
        // 依赖 MyBatis Plus @TableLogic 执行逻辑删除，不物理删除知识库及向量数据。
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
        // 查询时显式带上 tenantId，保证租户隔离。
        AiKnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectByIdAndTenantId(id, tenantId);
        if (knowledgeBase == null) {
            throw new ServiceException(KNOWLEDGE_NOT_EXISTS, "知识库不存在");
        }
        return knowledgeBase;
    }

    private void validateCodeUnique(Long tenantId, Long id, String code) {
        // code 唯一性只在当前租户范围内校验。
        AiKnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectByTenantIdAndCode(tenantId, code);
        if (knowledgeBase == null || knowledgeBase.getId().equals(id)) {
            return;
        }
        throw new ServiceException(KNOWLEDGE_CODE_DUPLICATE, "知识库编码在当前租户下已存在");
    }

    private String normalizeDepartmentIds(String departmentIds) {
        return departmentIds == null || departmentIds.isBlank() ? DEFAULT_DEPARTMENT_IDS : departmentIds.trim();
    }

    private String normalizeVisibility(String visibility) {
        return visibility == null || visibility.isBlank() ? DEFAULT_VISIBILITY : visibility.trim();
    }

    private Double normalizeScoreThreshold(Double scoreThreshold) {
        return scoreThreshold == null ? DEFAULT_SCORE_THRESHOLD : scoreThreshold;
    }

    private void validateVisibility(String visibility) {
        String normalizedVisibility = normalizeVisibility(visibility);
        if (!KnowledgeVisibilityEnum.isValidCode(normalizedVisibility)) {
            throw new ServiceException(KNOWLEDGE_VISIBILITY_INVALID, "知识库可见范围不支持");
        }
    }

    private void validateVectorStoreType(String vectorStoreType) {
        if (!VectorStoreTypeEnum.isValidCode(vectorStoreType)) {
            throw new ServiceException(KNOWLEDGE_VECTOR_STORE_TYPE_INVALID, "向量库类型不支持");
        }
    }

}
