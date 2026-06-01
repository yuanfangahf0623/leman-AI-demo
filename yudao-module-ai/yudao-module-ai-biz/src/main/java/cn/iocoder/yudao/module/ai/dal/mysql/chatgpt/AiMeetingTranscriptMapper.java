package cn.iocoder.yudao.module.ai.dal.mysql.chatgpt;

import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingTranscriptDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiMeetingTranscriptMapper extends BaseMapper<AiMeetingTranscriptDO> {

    default AiMeetingTranscriptDO selectByMeetingId(Long tenantId, Long meetingId) {
        return selectOne(new LambdaQueryWrapper<AiMeetingTranscriptDO>()
                .eq(AiMeetingTranscriptDO::getTenantId, tenantId)
                .eq(AiMeetingTranscriptDO::getMeetingId, meetingId)
                .last("LIMIT 1"));
    }

}
