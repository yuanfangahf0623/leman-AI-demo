package cn.iocoder.yudao.module.ai.dal.mysql.chatgpt;

import cn.iocoder.yudao.module.ai.dal.dataobject.chatgpt.AiChatGptActionLogDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatGptActionLogMapper extends BaseMapper<AiChatGptActionLogDO> {
}
