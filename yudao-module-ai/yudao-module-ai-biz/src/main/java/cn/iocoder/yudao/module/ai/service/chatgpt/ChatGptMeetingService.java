package cn.iocoder.yudao.module.ai.service.chatgpt;

import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingDetailRespVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingMinutesRespVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingRagSearchReqVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingRagSearchRespVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingSearchReqVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingSearchRespVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingTranscriptRespVO;

public interface ChatGptMeetingService {

    ChatGptMeetingSearchRespVO searchMeetings(ChatGptMeetingSearchReqVO reqVO);

    ChatGptMeetingDetailRespVO getMeeting(Long meetingId);

    ChatGptMeetingMinutesRespVO getMeetingMinutes(Long meetingId);

    ChatGptMeetingTranscriptRespVO getMeetingTranscript(Long meetingId, Integer maxChars);

    ChatGptMeetingRagSearchRespVO ragSearch(ChatGptMeetingRagSearchReqVO reqVO);

}
