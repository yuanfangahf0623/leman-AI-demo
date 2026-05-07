package cn.iocoder.yudao.module.ai.service.datasource;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourcePageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceUpdateReqVO;
import cn.iocoder.yudao.module.ai.convert.AiDataSourceConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDataSourceMapper;
import cn.iocoder.yudao.module.ai.enums.DataSourceTypeEnum;
import cn.iocoder.yudao.module.ai.enums.SyncModeEnum;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.knowledge.AiKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.module.ai.enums.AiDataSourceErrorCodeConstants.DATA_SOURCE_KNOWLEDGE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiDataSourceErrorCodeConstants.DATA_SOURCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiDataSourceErrorCodeConstants.DATA_SOURCE_SYNC_MODE_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiDataSourceErrorCodeConstants.DATA_SOURCE_TYPE_INVALID;

/**
 * AI data source service implementation.
 */
@Service
@RequiredArgsConstructor
public class AiDataSourceServiceImpl implements AiDataSourceService {

    private static final Integer DEFAULT_STATUS = 0;
    private static final Boolean DEFAULT_SYNC_ENABLED = false;

    private final AiDataSourceMapper dataSourceMapper;
    private final AiKnowledgeService knowledgeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDataSource(AiDataSourceCreateReqVO createReqVO) {
        validateKnowledgeExists(createReqVO.getKnowledgeBaseId());
        validateSourceType(createReqVO.getSourceType());
        validateSyncMode(createReqVO.getSyncMode());

        AiDataSourceDO dataSource = AiDataSourceConvert.INSTANCE.convert(createReqVO);
        dataSource.setTenantId(AiTenantContextHolder.getTenantId());
        dataSource.setSyncEnabled(dataSource.getSyncEnabled() != null ? dataSource.getSyncEnabled() : DEFAULT_SYNC_ENABLED);
        dataSource.setStatus(dataSource.getStatus() != null ? dataSource.getStatus() : DEFAULT_STATUS);
        dataSourceMapper.insert(dataSource);
        return dataSource.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDataSource(AiDataSourceUpdateReqVO updateReqVO) {
        AiDataSourceDO oldDataSource = validateDataSourceExists(updateReqVO.getId());
        validateKnowledgeExists(updateReqVO.getKnowledgeBaseId());
        validateSourceType(updateReqVO.getSourceType());
        validateSyncMode(updateReqVO.getSyncMode());

        AiDataSourceDO updateObj = AiDataSourceConvert.INSTANCE.convert(updateReqVO);
        updateObj.setTenantId(oldDataSource.getTenantId());
        updateObj.setLastSyncTime(oldDataSource.getLastSyncTime());
        updateObj.setSyncEnabled(updateObj.getSyncEnabled() != null ? updateObj.getSyncEnabled() : DEFAULT_SYNC_ENABLED);
        updateObj.setStatus(updateObj.getStatus() != null ? updateObj.getStatus() : DEFAULT_STATUS);
        dataSourceMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataSource(Long id) {
        validateDataSourceExists(id);
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
        AiDataSourceDO dataSource = dataSourceMapper.selectByIdAndTenantId(id, AiTenantContextHolder.getTenantId());
        if (dataSource == null) {
            throw new ServiceException(DATA_SOURCE_NOT_EXISTS, "数据源不存在");
        }
        return dataSource;
    }

    private void validateKnowledgeExists(Long knowledgeBaseId) {
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
