package cn.iocoder.yudao.module.ai.service.chatgpt;

import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingDO;
import cn.iocoder.yudao.module.ai.framework.chatgpt.ChatGptCallerContext;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ChatGptMeetingPermissionServiceImpl implements ChatGptMeetingPermissionService {

    public static final String SENSITIVITY_NORMAL = "NORMAL";
    public static final String SENSITIVITY_INTERNAL = "INTERNAL";
    public static final String SENSITIVITY_CONFIDENTIAL = "CONFIDENTIAL";
    public static final String SENSITIVITY_HR = "HR";
    public static final String SENSITIVITY_FINANCE = "FINANCE";

    private static final Set<String> MVP_ACCESSIBLE_LEVELS = Set.of(SENSITIVITY_NORMAL, SENSITIVITY_INTERNAL);

    @Override
    public Set<String> getMvpAccessibleSensitivityLevels() {
        return MVP_ACCESSIBLE_LEVELS;
    }

    @Override
    public boolean canAccessMeeting(AiMeetingDO meeting, ChatGptCallerContext caller) {
        if (meeting == null || caller == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(meeting.getChatgptVisible()) || Boolean.TRUE.equals(meeting.getDeleted())) {
            return false;
        }
        if (meeting.getTenantId() != null && !meeting.getTenantId().equals(caller.getTenantId())) {
            return false;
        }
        return MVP_ACCESSIBLE_LEVELS.contains(normalizeSensitivityLevel(meeting.getSensitivityLevel()));
    }

    @Override
    public boolean canReturnTranscript(AiMeetingDO meeting, ChatGptCallerContext caller) {
        return canAccessMeeting(meeting, caller)
                && SENSITIVITY_NORMAL.equals(normalizeSensitivityLevel(meeting.getSensitivityLevel()));
    }

    @Override
    public boolean canReturnSourceQuote(AiMeetingDO meeting, ChatGptCallerContext caller) {
        return canAccessMeeting(meeting, caller);
    }

    private static String normalizeSensitivityLevel(String sensitivityLevel) {
        return sensitivityLevel == null ? SENSITIVITY_NORMAL : sensitivityLevel.toUpperCase();
    }

}
