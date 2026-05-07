package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatMessageDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 问答消息 Mapper。
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessageDO> {
}
