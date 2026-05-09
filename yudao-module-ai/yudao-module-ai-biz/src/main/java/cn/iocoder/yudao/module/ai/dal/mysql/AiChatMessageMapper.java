package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatMessageDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI 问答消息 Mapper。
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessageDO> {

    default AiChatMessageDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(AiChatMessageDO.class)
                .eq(AiChatMessageDO::getId, id)
                .eq(AiChatMessageDO::getTenantId, tenantId));
    }

    default List<AiChatMessageDO> selectListByConversationId(Long conversationId, Long tenantId) {
        return selectList(Wrappers.lambdaQuery(AiChatMessageDO.class)
                .eq(AiChatMessageDO::getConversationId, conversationId)
                .eq(AiChatMessageDO::getTenantId, tenantId)
                .orderByAsc(AiChatMessageDO::getId));
    }

}
