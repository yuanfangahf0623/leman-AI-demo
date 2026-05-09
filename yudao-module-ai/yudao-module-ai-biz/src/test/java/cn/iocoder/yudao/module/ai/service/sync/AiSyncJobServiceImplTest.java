package cn.iocoder.yudao.module.ai.service.sync;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.controller.admin.sync.vo.AiSyncJobCreateReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiSyncJobMapper;
import cn.iocoder.yudao.module.ai.enums.SyncJobStatusEnum;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.datasource.AiDataSourceService;
import cn.iocoder.yudao.module.ai.service.knowledge.AiKnowledgeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_MISMATCH;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_KNOWLEDGE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSyncJobServiceImplTest {

    @Mock
    private AiSyncJobMapper syncJobMapper;
    @Mock
    private AiKnowledgeService knowledgeService;
    @Mock
    private AiDataSourceService dataSourceService;

    private AiSyncJobServiceImpl syncJobService;

    @BeforeEach
    void setUp() {
        AiTenantContextHolder.setTenantId(1L);
        syncJobService = new AiSyncJobServiceImpl(syncJobMapper, knowledgeService, dataSourceService);
    }

    @AfterEach
    void tearDown() {
        AiTenantContextHolder.clear();
    }

    @Test
    void createSyncJobShouldInsertPendingJob() {
        AiSyncJobCreateReqVO reqVO = buildReqVO();
        when(knowledgeService.getKnowledge(1001L)).thenReturn(AiKnowledgeBaseDO.builder().id(1001L).build());
        when(dataSourceService.getDataSource(2001L)).thenReturn(AiDataSourceDO.builder()
                .id(2001L).knowledgeBaseId(1001L).build());
        doAnswer(invocation -> {
            AiSyncJobDO syncJob = invocation.getArgument(0);
            syncJob.setId(3001L);
            return 1;
        }).when(syncJobMapper).insert(any(AiSyncJobDO.class));

        Long jobId = syncJobService.createSyncJob(reqVO);

        assertEquals(3001L, jobId);
        ArgumentCaptor<AiSyncJobDO> captor = ArgumentCaptor.forClass(AiSyncJobDO.class);
        verify(syncJobMapper).insert(captor.capture());
        AiSyncJobDO syncJob = captor.getValue();
        assertEquals(1L, syncJob.getTenantId());
        assertEquals(1001L, syncJob.getKnowledgeBaseId());
        assertEquals(2001L, syncJob.getDataSourceId());
        assertEquals("INCREMENTAL", syncJob.getJobType());
        assertEquals("MANUAL", syncJob.getTriggerType());
        assertEquals(SyncJobStatusEnum.PENDING.getCode(), syncJob.getStatus());
        assertEquals(0, syncJob.getTotalCount());
        assertEquals(0, syncJob.getSuccessCount());
        assertEquals(0, syncJob.getFailCount());
    }

    @Test
    void createSyncJobShouldRejectMissingKnowledge() {
        AiSyncJobCreateReqVO reqVO = buildReqVO();

        ServiceException exception = assertThrows(ServiceException.class, () -> syncJobService.createSyncJob(reqVO));

        assertEquals(SYNC_JOB_KNOWLEDGE_NOT_EXISTS, exception.getCode());
    }

    @Test
    void createSyncJobShouldRejectMissingDataSource() {
        AiSyncJobCreateReqVO reqVO = buildReqVO();
        when(knowledgeService.getKnowledge(1001L)).thenReturn(AiKnowledgeBaseDO.builder().id(1001L).build());

        ServiceException exception = assertThrows(ServiceException.class, () -> syncJobService.createSyncJob(reqVO));

        assertEquals(SYNC_JOB_DATA_SOURCE_NOT_EXISTS, exception.getCode());
    }

    @Test
    void createSyncJobShouldRejectMismatchedDataSource() {
        AiSyncJobCreateReqVO reqVO = buildReqVO();
        when(knowledgeService.getKnowledge(1001L)).thenReturn(AiKnowledgeBaseDO.builder().id(1001L).build());
        when(dataSourceService.getDataSource(2001L)).thenReturn(AiDataSourceDO.builder()
                .id(2001L).knowledgeBaseId(9999L).build());

        ServiceException exception = assertThrows(ServiceException.class, () -> syncJobService.createSyncJob(reqVO));

        assertEquals(SYNC_JOB_DATA_SOURCE_MISMATCH, exception.getCode());
    }

    private AiSyncJobCreateReqVO buildReqVO() {
        AiSyncJobCreateReqVO reqVO = new AiSyncJobCreateReqVO();
        reqVO.setKnowledgeBaseId(1001L);
        reqVO.setDataSourceId(2001L);
        reqVO.setJobType("INCREMENTAL");
        return reqVO;
    }

}
