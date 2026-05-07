package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncRecordDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 同步记录 Mapper。
 */
@Mapper
public interface AiSyncRecordMapper extends BaseMapper<AiSyncRecordDO> {
}
