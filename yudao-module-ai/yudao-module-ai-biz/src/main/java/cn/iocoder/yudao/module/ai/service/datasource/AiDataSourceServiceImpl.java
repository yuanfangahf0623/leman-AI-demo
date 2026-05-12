package cn.iocoder.yudao.module.ai.service.datasource;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceIngestReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceIngestRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourcePageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceUpdateReqVO;
import cn.iocoder.yudao.module.ai.convert.AiDataSourceConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDataSourceMapper;
import cn.iocoder.yudao.module.ai.enums.DataSourceTypeEnum;
import cn.iocoder.yudao.module.ai.enums.SyncModeEnum;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.document.AiDocumentService;
import cn.iocoder.yudao.module.ai.service.knowledge.AiKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.module.ai.enums.AiDataSourceErrorCodeConstants.DATA_SOURCE_KNOWLEDGE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiDataSourceErrorCodeConstants.DATA_SOURCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiDataSourceErrorCodeConstants.DATA_SOURCE_SYNC_MODE_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiDataSourceErrorCodeConstants.DATA_SOURCE_TYPE_INVALID;

/**
 * AI 数据源 Service 实现。
 *
 * <p>负责数据源 CRUD 编排，包括知识库归属校验、sourceType/syncMode 合法性校验和逻辑删除。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiDataSourceServiceImpl implements AiDataSourceService {

    private static final Integer DEFAULT_STATUS = 0;
    private static final Boolean DEFAULT_SYNC_ENABLED = false;

    private final AiDataSourceMapper dataSourceMapper;
    private final AiKnowledgeService knowledgeService;
    private final AiDocumentService documentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDataSource(AiDataSourceCreateReqVO createReqVO) {
        // 数据源必须归属于当前租户可访问的知识库。
        validateKnowledgeExists(createReqVO.getKnowledgeBaseId());
        // 第一阶段只允许明确列出的数据源类型和同步模式。
        validateSourceType(createReqVO.getSourceType());
        validateSyncMode(createReqVO.getSyncMode());

        AiDataSourceDO dataSource = AiDataSourceConvert.INSTANCE.convert(createReqVO);
        dataSource.setTenantId(AiTenantContextHolder.getTenantId());
        // 未显式传入时使用开发阶段默认值，避免数据库空值。
        dataSource.setSyncEnabled(dataSource.getSyncEnabled() != null ? dataSource.getSyncEnabled() : DEFAULT_SYNC_ENABLED);
        dataSource.setStatus(dataSource.getStatus() != null ? dataSource.getStatus() : DEFAULT_STATUS);
        dataSourceMapper.insert(dataSource);
        return dataSource.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiDataSourceIngestRespVO ingest(AiDataSourceIngestReqVO ingestReqVO) {
        AiDataSourceDO dataSource = validateDataSourceExists(ingestReqVO.getDataSourceId());
        if (!DataSourceTypeEnum.API.getCode().equals(dataSource.getType())) {
            throw new ServiceException(DATA_SOURCE_TYPE_INVALID, "只有 API 数据源支持 Webhook 写入");
        }
        validateKnowledgeExists(dataSource.getKnowledgeBaseId());
        AiDataSourceIngestRespVO response = documentService.createDocumentFromDataSource(dataSource, ingestReqVO);
        log.info("API 数据源写入完成, tenantId={}, knowledgeBaseId={}, dataSourceId={}, documentId={}, action={}",
                dataSource.getTenantId(), dataSource.getKnowledgeBaseId(), dataSource.getId(),
                response.getDocumentId(), response.getAction());
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDataSource(AiDataSourceUpdateReqVO updateReqVO) {
        // 更新前确认数据源存在且属于当前租户。
        AiDataSourceDO oldDataSource = validateDataSourceExists(updateReqVO.getId());
        // 支持调整归属知识库，但目标知识库也必须属于当前租户。
        validateKnowledgeExists(updateReqVO.getKnowledgeBaseId());
        validateSourceType(updateReqVO.getSourceType());
        validateSyncMode(updateReqVO.getSyncMode());

        AiDataSourceDO updateObj = AiDataSourceConvert.INSTANCE.convert(updateReqVO);
        updateObj.setTenantId(oldDataSource.getTenantId());
        // 同步时间由同步任务维护，基础信息更新不覆盖它。
        updateObj.setLastSyncTime(oldDataSource.getLastSyncTime());
        updateObj.setSyncEnabled(updateObj.getSyncEnabled() != null ? updateObj.getSyncEnabled() : DEFAULT_SYNC_ENABLED);
        updateObj.setStatus(updateObj.getStatus() != null ? updateObj.getStatus() : DEFAULT_STATUS);
        dataSourceMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataSource(Long id) {
        validateDataSourceExists(id);
        // 只逻辑删除数据源，不级联删除 ai_document，避免误删已上传文档。
        dataSourceMapper.deleteById(id);
    }

    @Override
    public AiDataSourceDO getDataSource(Long id) {
        return dataSourceMapper.selectByIdAndTenantId(id, AiTenantContextHolder.getTenantId());
    }

    @Override
    public PageResult<AiDataSourceDO> getDataSourcePage(AiDataSourcePageReqVO pageReqVO) {
        return dataSourceMapper.selectPage(pageReqVO, AiTenantContextHolder.getTenantId());
    }

    private AiDataSourceDO validateDataSourceExists(Long id) {
        // 所有数据源读写都显式带上当前租户 ID。
        AiDataSourceDO dataSource = dataSourceMapper.selectByIdAndTenantId(id, AiTenantContextHolder.getTenantId());
        if (dataSource == null) {
            throw new ServiceException(DATA_SOURCE_NOT_EXISTS, "数据源不存在");
        }
        return dataSource;
    }

    private void validateKnowledgeExists(Long knowledgeBaseId) {
        // 复用知识库 Service 的租户过滤能力，非法 knowledgeBaseId 会被拒绝。
        if (knowledgeService.getKnowledge(knowledgeBaseId) == null) {
            throw new ServiceException(DATA_SOURCE_KNOWLEDGE_NOT_EXISTS, "知识库不存在");
        }
    }

    private void validateSourceType(String sourceType) {
        if (!DataSourceTypeEnum.isValidCode(sourceType)) {
            throw new ServiceException(DATA_SOURCE_TYPE_INVALID, "数据源类型不支持");
        }
    }

    private void validateSyncMode(String syncMode) {
        if (!SyncModeEnum.isValidCode(syncMode)) {
            throw new ServiceException(DATA_SOURCE_SYNC_MODE_INVALID, "同步模式不支持");
        }
    }

}
