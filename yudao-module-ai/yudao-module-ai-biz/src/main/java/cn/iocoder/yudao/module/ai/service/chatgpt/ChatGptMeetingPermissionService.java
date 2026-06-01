package cn.iocoder.yudao.module.ai.service.chatgpt;

import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingDO;
import cn.iocoder.yudao.module.ai.framework.chatgpt.ChatGptCallerContext;

import java.util.Set;

public interface ChatGptMeetingPermissionService {

    Set<String> getMvpAccessibleSensitivityLevels();

    boolean canAccessMeeting(AiMeetingDO meeting, ChatGptCallerContext caller);

    boolean canReturnTranscript(AiMeetingDO meeting, ChatGptCallerContext caller);

    boolean canReturnSourceQuote(AiMeetingDO meeting, ChatGptCallerContext caller);

}
