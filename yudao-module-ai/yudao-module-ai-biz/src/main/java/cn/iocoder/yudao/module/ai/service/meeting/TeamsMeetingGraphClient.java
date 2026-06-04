package cn.iocoder.yudao.module.ai.service.meeting;

import cn.iocoder.yudao.module.ai.service.meeting.dto.TeamsMeetingDTO;
import cn.iocoder.yudao.module.ai.service.meeting.dto.TeamsTranscriptDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TeamsMeetingGraphClient {

    List<TeamsMeetingDTO> listMeetings(String organizerUserId, LocalDate startDate, LocalDate endDate, int limit);

    Optional<TeamsTranscriptDTO> getTranscript(String organizerUserId, TeamsMeetingDTO meeting);

}
