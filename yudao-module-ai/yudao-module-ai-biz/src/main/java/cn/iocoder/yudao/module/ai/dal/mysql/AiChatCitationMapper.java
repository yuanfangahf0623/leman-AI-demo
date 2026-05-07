package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 引用来源 Mapper。
 */
@Mapper
public interface AiChatCitationMapper extends BaseMapper<AiChatCitationDO> {
}
