package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI 引用来源 Mapper。
 */
@Mapper
public interface AiChatCitationMapper extends BaseMapper<AiChatCitationDO> {

    default List<AiChatCitationDO> selectListByMessageId(Long messageId, Long tenantId) {
        return selectList(Wrappers.lambdaQuery(AiChatCitationDO.class)
                .eq(AiChatCitationDO::getMessageId, messageId)
                .eq(AiChatCitationDO::getTenantId, tenantId)
                .orderByAsc(AiChatCitationDO::getSortOrder)
                .orderByAsc(AiChatCitationDO::getId));
    }

}
