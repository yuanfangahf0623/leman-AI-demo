package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * AI 同步任务 Mapper。
 */
@Mapper
public interface AiSyncJobMapper extends BaseMapper<AiSyncJobDO> {

    default AiSyncJobDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(AiSyncJobDO.class)
                .eq(AiSyncJobDO::getId, id)
                .eq(AiSyncJobDO::getTenantId, tenantId));
    }

    default int updateRunningByIdAndTenantId(Long id, Long tenantId, Integer status, LocalDateTime startTime) {
        return update(null, Wrappers.lambdaUpdate(AiSyncJobDO.class)
                .set(AiSyncJobDO::getStatus, status)
                .set(AiSyncJobDO::getStartTime, startTime)
                .set(AiSyncJobDO::getEndTime, null)
                .set(AiSyncJobDO::getErrorMessage, null)
                .eq(AiSyncJobDO::getId, id)
                .eq(AiSyncJobDO::getTenantId, tenantId));
    }

    default int updateResultByIdAndTenantId(Long id, Long tenantId, Integer totalCount, Integer successCount,
                                            Integer failCount, Integer status, LocalDateTime endTime,
                                            String errorMessage) {
        return update(null, Wrappers.lambdaUpdate(AiSyncJobDO.class)
                .set(AiSyncJobDO::getTotalCount, totalCount)
                .set(AiSyncJobDO::getSuccessCount, successCount)
                .set(AiSyncJobDO::getFailCount, failCount)
                .set(AiSyncJobDO::getStatus, status)
                .set(AiSyncJobDO::getEndTime, endTime)
                .set(AiSyncJobDO::getErrorMessage, errorMessage)
                .eq(AiSyncJobDO::getId, id)
                .eq(AiSyncJobDO::getTenantId, tenantId));
    }

}
