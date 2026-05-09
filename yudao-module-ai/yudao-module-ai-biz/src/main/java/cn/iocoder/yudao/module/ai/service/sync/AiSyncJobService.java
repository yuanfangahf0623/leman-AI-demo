package cn.iocoder.yudao.module.ai.service.sync;

import cn.iocoder.yudao.module.ai.controller.admin.sync.vo.AiSyncJobCreateReqVO;

/**
 * AI sync job service.
 */
public interface AiSyncJobService {

    Long createSyncJob(AiSyncJobCreateReqVO createReqVO);

}
