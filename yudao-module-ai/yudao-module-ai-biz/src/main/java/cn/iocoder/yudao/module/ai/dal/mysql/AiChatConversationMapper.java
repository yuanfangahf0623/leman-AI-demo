package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 问答会话 Mapper。
 */
@Mapper
public interface AiChatConversationMapper extends BaseMapper<AiChatConversationDO> {
}
