package cn.iocoder.yudao.module.ai.dal.mysql.chatgpt;

import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingMinutesDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiMeetingMinutesMapper extends BaseMapper<AiMeetingMinutesDO> {

    default AiMeetingMinutesDO selectByMeetingId(Long tenantId, Long meetingId) {
        return selectOne(new LambdaQueryWrapper<AiMeetingMinutesDO>()
                .eq(AiMeetingMinutesDO::getTenantId, tenantId)
                .eq(AiMeetingMinutesDO::getMeetingId, meetingId)
                .last("LIMIT 1"));
    }

}
