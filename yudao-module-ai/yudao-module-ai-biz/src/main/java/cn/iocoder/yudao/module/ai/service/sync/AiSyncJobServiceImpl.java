package cn.iocoder.yudao.module.ai.service.sync;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.controller.admin.sync.vo.AiSyncJobCreateReqVO;
import cn.iocoder.yudao.module.ai.convert.AiSyncJobConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiSyncJobMapper;
import cn.iocoder.yudao.module.ai.enums.SyncJobStatusEnum;
import cn.iocoder.yudao.module.ai.enums.SyncJobTypeEnum;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.datasource.AiDataSourceService;
import cn.iocoder.yudao.module.ai.service.knowledge.AiKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_MISMATCH;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_KNOWLEDGE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_TYPE_INVALID;

/**
 * AI 同步任务 Service 实现。
 *
 * <p>第一阶段只负责创建待执行任务，真实同步后续由异步调度或 MQ 编排。</p>
 */
@Service
@RequiredArgsConstructor
public class AiSyncJobServiceImpl implements AiSyncJobService {

    private static final String DEFAULT_TRIGGER_TYPE = "MANUAL";
    private static final Integer DEFAULT_COUNT = 0;

    private final AiSyncJobMapper syncJobMapper;
    private final AiKnowledgeService knowledgeService;
    private final AiDataSourceService dataSourceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSyncJob(AiSyncJobCreateReqVO createReqVO) {
        // 创建同步任务前先校验知识库和数据源都属于当前租户。
        validateKnowledgeExists(createReqVO.getKnowledgeBaseId());
        AiDataSourceDO dataSource = validateDataSourceExists(createReqVO.getDataSourceId());
        validateDataSourceBelongsToKnowledge(dataSource, createReqVO.getKnowledgeBaseId());
        validateJobType(createReqVO.getJobType());

        AiSyncJobDO syncJob = AiSyncJobConvert.INSTANCE.convert(createReqVO);
        syncJob.setTenantId(AiTenantContextHolder.getTenantId());
        syncJob.setTriggerType(DEFAULT_TRIGGER_TYPE);
        syncJob.setStatus(SyncJobStatusEnum.PENDING.getCode());
        syncJob.setTotalCount(DEFAULT_COUNT);
        syncJob.setSuccessCount(DEFAULT_COUNT);
        syncJob.setFailCount(DEFAULT_COUNT);
        syncJobMapper.insert(syncJob);
        return syncJob.getId();
    }

    private void validateKnowledgeExists(Long knowledgeBaseId) {
        if (knowledgeService.getKnowledge(knowledgeBaseId) == null) {
            throw new ServiceException(SYNC_JOB_KNOWLEDGE_NOT_EXISTS, "知识库不存在");
        }
    }

    private AiDataSourceDO validateDataSourceExists(Long dataSourceId) {
        AiDataSourceDO dataSource = dataSourceService.getDataSource(dataSourceId);
        if (dataSource == null) {
            throw new ServiceException(SYNC_JOB_DATA_SOURCE_NOT_EXISTS, "数据源不存在");
        }
        return dataSource;
    }

    private void validateDataSourceBelongsToKnowledge(AiDataSourceDO dataSource, Long knowledgeBaseId) {
        if (!knowledgeBaseId.equals(dataSource.getKnowledgeBaseId())) {
            throw new ServiceException(SYNC_JOB_DATA_SOURCE_MISMATCH, "数据源不属于当前知识库");
        }
    }

    private void validateJobType(String jobType) {
        if (!SyncJobTypeEnum.isValidCode(jobType)) {
            throw new ServiceException(SYNC_JOB_TYPE_INVALID, "同步任务类型不支持");
        }
    }

}
