package cn.iocoder.yudao.module.ai.controller.admin.meeting;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.ai.controller.admin.meeting.vo.AiTeamsMeetingSyncReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.meeting.vo.AiTeamsMeetingSyncRespVO;
import cn.iocoder.yudao.module.ai.service.meeting.TeamsMeetingSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin-api/ai/teams-meeting")
@Validated
@RequiredArgsConstructor
public class AiTeamsMeetingSyncController {

    private final TeamsMeetingSyncService teamsMeetingSyncService;

    @PostMapping("/sync")
    @PreAuthorize("@ss.hasPermission('ai:teams-meeting:sync')")
    public CommonResult<AiTeamsMeetingSyncRespVO> syncMeetings(@Valid @RequestBody AiTeamsMeetingSyncReqVO reqVO) {
        return CommonResult.success(teamsMeetingSyncService.syncMeetings(reqVO));
    }

}
