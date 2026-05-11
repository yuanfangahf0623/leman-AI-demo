package cn.iocoder.yudao.module.ai.service.knowledge;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryListReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryUpdateReqVO;
import cn.iocoder.yudao.module.ai.convert.AiKnowledgeDirectoryConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeDirectoryDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiKnowledgeDirectoryMapper;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.iocoder.yudao.module.ai.enums.AiKnowledgeErrorCodeConstants.KNOWLEDGE_DIRECTORY_HAS_CHILDREN;
import static cn.iocoder.yudao.module.ai.enums.AiKnowledgeErrorCodeConstants.KNOWLEDGE_DIRECTORY_HAS_DOCUMENTS;
import static cn.iocoder.yudao.module.ai.enums.AiKnowledgeErrorCodeConstants.KNOWLEDGE_DIRECTORY_NAME_DUPLICATE;
import static cn.iocoder.yudao.module.ai.enums.AiKnowledgeErrorCodeConstants.KNOWLEDGE_DIRECTORY_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiKnowledgeErrorCodeConstants.KNOWLEDGE_DIRECTORY_PARENT_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiKnowledgeErrorCodeConstants.KNOWLEDGE_NOT_EXISTS;

/**
 * AI 知识库目录 Service 实现。
 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeDirectoryServiceImpl implements AiKnowledgeDirectoryService {

    private static final Long ROOT_PARENT_ID = 0L;
    private static final Integer DEFAULT_SORT = 0;
    private static final Integer DEFAULT_STATUS = 0;

    private final AiKnowledgeDirectoryMapper directoryMapper;
    private final AiDocumentMapper documentMapper;
    private final AiKnowledgeService knowledgeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDirectory(AiKnowledgeDirectoryCreateReqVO createReqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        validateKnowledgeExists(createReqVO.getKnowledgeBaseId());
        Long parentId = normalizeParentId(createReqVO.getParentId());
        validateParentDirectory(tenantId, createReqVO.getKnowledgeBaseId(), parentId, null);
        validateNameUnique(tenantId, null, createReqVO.getKnowledgeBaseId(), parentId, createReqVO.getName());

        AiKnowledgeDirectoryDO directory = AiKnowledgeDirectoryConvert.INSTANCE.convert(createReqVO);
        directory.setTenantId(tenantId);
        directory.setParentId(parentId);
        directory.setSort(directory.getSort() == null ? DEFAULT_SORT : directory.getSort());
        directory.setStatus(directory.getStatus() == null ? DEFAULT_STATUS : directory.getStatus());
        directoryMapper.insert(directory);
        return directory.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDirectory(AiKnowledgeDirectoryUpdateReqVO updateReqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        AiKnowledgeDirectoryDO oldDirectory = validateDirectoryExists(updateReqVO.getId(), tenantId);
        validateKnowledgeExists(updateReqVO.getKnowledgeBaseId());
        Long parentId = normalizeParentId(updateReqVO.getParentId());
        validateParentDirectory(tenantId, updateReqVO.getKnowledgeBaseId(), parentId, updateReqVO.getId());
        validateNameUnique(tenantId, updateReqVO.getId(), updateReqVO.getKnowledgeBaseId(), parentId,
                updateReqVO.getName());

        AiKnowledgeDirectoryDO updateObj = AiKnowledgeDirectoryConvert.INSTANCE.convert(updateReqVO);
        updateObj.setTenantId(oldDirectory.getTenantId());
        updateObj.setParentId(parentId);
        updateObj.setSort(updateObj.getSort() == null ? DEFAULT_SORT : updateObj.getSort());
        updateObj.setStatus(updateObj.getStatus() == null ? DEFAULT_STATUS : updateObj.getStatus());
        directoryMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDirectory(Long id) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        AiKnowledgeDirectoryDO directory = validateDirectoryExists(id, tenantId);
        Long childCount = directoryMapper.selectCountByParentId(tenantId, directory.getKnowledgeBaseId(), id);
        if (childCount != null && childCount > 0) {
            throw new ServiceException(KNOWLEDGE_DIRECTORY_HAS_CHILDREN, "目录下存在子目录，不能删除");
        }
        Long documentCount = documentMapper.selectCountByDirectoryId(tenantId, id);
        if (documentCount != null && documentCount > 0) {
            throw new ServiceException(KNOWLEDGE_DIRECTORY_HAS_DOCUMENTS, "目录下存在文档，不能删除");
        }
        directoryMapper.deleteById(id);
    }

    @Override
    public AiKnowledgeDirectoryDO getDirectory(Long id) {
        return validateDirectoryExists(id, AiTenantContextHolder.getTenantId());
    }

    @Override
    public List<AiKnowledgeDirectoryDO> getDirectoryList(AiKnowledgeDirectoryListReqVO listReqVO) {
        validateKnowledgeExists(listReqVO.getKnowledgeBaseId());
        return directoryMapper.selectListByKnowledgeBaseId(AiTenantContextHolder.getTenantId(),
                listReqVO.getKnowledgeBaseId());
    }

    private AiKnowledgeBaseDO validateKnowledgeExists(Long knowledgeBaseId) {
        AiKnowledgeBaseDO knowledgeBase = knowledgeService.getKnowledge(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new ServiceException(KNOWLEDGE_NOT_EXISTS, "知识库不存在");
        }
        return knowledgeBase;
    }

    private AiKnowledgeDirectoryDO validateDirectoryExists(Long id, Long tenantId) {
        AiKnowledgeDirectoryDO directory = directoryMapper.selectByIdAndTenantId(id, tenantId);
        if (directory == null) {
            throw new ServiceException(KNOWLEDGE_DIRECTORY_NOT_EXISTS, "目录不存在");
        }
        return directory;
    }

    private void validateParentDirectory(Long tenantId, Long knowledgeBaseId, Long parentId, Long selfId) {
        if (ROOT_PARENT_ID.equals(parentId)) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new ServiceException(KNOWLEDGE_DIRECTORY_PARENT_INVALID, "上级目录不能选择自己");
        }
        AiKnowledgeDirectoryDO parent = directoryMapper.selectByIdAndTenantId(parentId, tenantId);
        if (parent == null || !knowledgeBaseId.equals(parent.getKnowledgeBaseId())) {
            throw new ServiceException(KNOWLEDGE_DIRECTORY_PARENT_INVALID, "上级目录不存在或不属于当前知识库");
        }
        validateNotMoveToChild(tenantId, knowledgeBaseId, parent, selfId);
    }

    private void validateNameUnique(Long tenantId, Long id, Long knowledgeBaseId, Long parentId, String name) {
        AiKnowledgeDirectoryDO directory = directoryMapper.selectByName(tenantId, knowledgeBaseId, parentId, name);
        if (directory == null || directory.getId().equals(id)) {
            return;
        }
        throw new ServiceException(KNOWLEDGE_DIRECTORY_NAME_DUPLICATE, "同级目录名称已存在");
    }

    private void validateNotMoveToChild(Long tenantId, Long knowledgeBaseId, AiKnowledgeDirectoryDO parent,
                                        Long selfId) {
        if (selfId == null) {
            return;
        }
        Long cursorParentId = parent.getParentId();
        while (cursorParentId != null && !ROOT_PARENT_ID.equals(cursorParentId)) {
            if (cursorParentId.equals(selfId)) {
                throw new ServiceException(KNOWLEDGE_DIRECTORY_PARENT_INVALID, "不能将目录移动到自己的子目录下");
            }
            AiKnowledgeDirectoryDO ancestor = directoryMapper.selectByIdAndTenantId(cursorParentId, tenantId);
            if (ancestor == null || !knowledgeBaseId.equals(ancestor.getKnowledgeBaseId())) {
                break;
            }
            cursorParentId = ancestor.getParentId();
        }
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null ? ROOT_PARENT_ID : parentId;
    }

}
