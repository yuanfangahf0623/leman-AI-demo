package cn.iocoder.yudao.module.ai.controller.admin.sync;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.ai.controller.admin.sync.vo.AiSyncJobCreateReqVO;
import cn.iocoder.yudao.module.ai.service.sync.AiSyncJobService;
import cn.iocoder.yudao.module.ai.service.sync.KnowledgeSyncService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiSyncJobControllerTest {

    @Test
    void createSyncJobShouldReturnJobId() {
        AiSyncJobService syncJobService = mock(AiSyncJobService.class);
        KnowledgeSyncService knowledgeSyncService = mock(KnowledgeSyncService.class);
        AiSyncJobController controller = new AiSyncJobController(syncJobService, knowledgeSyncService);
        AiSyncJobCreateReqVO reqVO = new AiSyncJobCreateReqVO();
        reqVO.setKnowledgeBaseId(1001L);
        reqVO.setDataSourceId(2001L);
        reqVO.setJobType("INCREMENTAL");
        when(syncJobService.createSyncJob(reqVO)).thenReturn(3001L);

        CommonResult<Long> result = controller.createSyncJob(reqVO);

        assertEquals(CommonResult.SUCCESS_CODE, result.getCode());
        assertEquals(3001L, result.getData());
        verify(syncJobService).createSyncJob(reqVO);
    }

    @Test
    void executeSyncJobShouldCallKnowledgeSyncService() {
        AiSyncJobService syncJobService = mock(AiSyncJobService.class);
        KnowledgeSyncService knowledgeSyncService = mock(KnowledgeSyncService.class);
        AiSyncJobController controller = new AiSyncJobController(syncJobService, knowledgeSyncService);

        CommonResult<Boolean> result = controller.executeSyncJob(3001L);

        assertEquals(CommonResult.SUCCESS_CODE, result.getCode());
        assertEquals(true, result.getData());
        verify(knowledgeSyncService).executeSyncJob(3001L);
    }

}
