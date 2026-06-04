package cn.iocoder.yudao.module.ai.service.meeting;

import cn.iocoder.yudao.module.ai.service.meeting.dto.TeamsMeetingDTO;
import cn.iocoder.yudao.module.ai.service.meeting.dto.TeamsMeetingMinutesDTO;

public interface TeamsMeetingMinutesGenerator {

    TeamsMeetingMinutesDTO generate(TeamsMeetingDTO meeting, String cleanedTranscript);

}
