package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 同步任务 Mapper。
 */
@Mapper
public interface AiSyncJobMapper extends BaseMapper<AiSyncJobDO> {
}
