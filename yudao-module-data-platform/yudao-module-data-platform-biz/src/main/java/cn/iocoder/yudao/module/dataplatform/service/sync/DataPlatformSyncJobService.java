package cn.iocoder.yudao.module.dataplatform.service.sync;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.JobRunPageReqVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.SyncJobPageReqVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.SyncJobSaveReqVO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformJobRunDO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformSyncJobDO;

public interface DataPlatformSyncJobService {
    Long create(SyncJobSaveReqVO reqVO);
    void update(SyncJobSaveReqVO reqVO);
    void delete(Long id);
    DataPlatformSyncJobDO get(Long id);
    PageResult<DataPlatformSyncJobDO> page(SyncJobPageReqVO reqVO);
    Long execute(Long id, String creator);
    PageResult<DataPlatformJobRunDO> runPage(JobRunPageReqVO reqVO);
    DataPlatformJobRunDO getRun(Long id);
    String readRunLog(Long id, int tailLines);
}
