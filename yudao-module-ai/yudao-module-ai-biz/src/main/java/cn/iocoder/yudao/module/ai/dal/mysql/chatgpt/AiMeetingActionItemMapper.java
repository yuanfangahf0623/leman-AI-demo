package cn.iocoder.yudao.module.ai.dal.mysql.chatgpt;

import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingActionItemDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiMeetingActionItemMapper extends BaseMapper<AiMeetingActionItemDO> {

    default List<AiMeetingActionItemDO> selectListByMeetingId(Long tenantId, Long meetingId) {
        return selectList(new LambdaQueryWrapper<AiMeetingActionItemDO>()
                .eq(AiMeetingActionItemDO::getTenantId, tenantId)
                .eq(AiMeetingActionItemDO::getMeetingId, meetingId)
                .orderByAsc(AiMeetingActionItemDO::getId));
    }

    default int deleteByMeetingId(Long tenantId, Long meetingId) {
        return delete(new LambdaQueryWrapper<AiMeetingActionItemDO>()
                .eq(AiMeetingActionItemDO::getTenantId, tenantId)
                .eq(AiMeetingActionItemDO::getMeetingId, meetingId));
    }

}
