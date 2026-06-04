package cn.iocoder.yudao.module.ai.service.meeting;

import cn.iocoder.yudao.module.ai.controller.admin.meeting.vo.AiTeamsMeetingSyncReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.meeting.vo.AiTeamsMeetingSyncRespVO;

public interface TeamsMeetingSyncService {

    AiTeamsMeetingSyncRespVO syncMeetings(AiTeamsMeetingSyncReqVO reqVO);

}
