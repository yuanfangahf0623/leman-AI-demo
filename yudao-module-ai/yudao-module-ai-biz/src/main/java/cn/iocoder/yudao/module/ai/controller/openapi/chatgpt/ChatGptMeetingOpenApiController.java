package cn.iocoder.yudao.module.ai.controller.openapi.chatgpt;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingDetailRespVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingMinutesRespVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingRagSearchReqVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingRagSearchRespVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingSearchReqVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingSearchRespVO;
import cn.iocoder.yudao.module.ai.controller.openapi.chatgpt.vo.ChatGptMeetingTranscriptRespVO;
import cn.iocoder.yudao.module.ai.service.chatgpt.ChatGptMeetingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/openapi/chatgpt/meetings")
public class ChatGptMeetingOpenApiController {

    private final ChatGptMeetingService chatGptMeetingService;

    @GetMapping("/search")
    public CommonResult<ChatGptMeetingSearchRespVO> searchMeetings(@Valid ChatGptMeetingSearchReqVO reqVO) {
        return success(chatGptMeetingService.searchMeetings(reqVO));
    }

    @GetMapping("/{meetingId}")
    public CommonResult<ChatGptMeetingDetailRespVO> getMeeting(@PathVariable("meetingId") @Min(1) Long meetingId) {
        return success(chatGptMeetingService.getMeeting(meetingId));
    }

    @GetMapping("/{meetingId}/minutes")
    public CommonResult<ChatGptMeetingMinutesRespVO> getMeetingMinutes(@PathVariable("meetingId") @Min(1) Long meetingId) {
        return success(chatGptMeetingService.getMeetingMinutes(meetingId));
    }

    @GetMapping("/{meetingId}/transcript")
    public CommonResult<ChatGptMeetingTranscriptRespVO> getMeetingTranscript(
            @PathVariable("meetingId") @Min(1) Long meetingId,
            @RequestParam(value = "maxChars", required = false) @Min(1) @Max(50000) Integer maxChars) {
        return success(chatGptMeetingService.getMeetingTranscript(meetingId, maxChars));
    }

    @PostMapping("/rag-search")
    public CommonResult<ChatGptMeetingRagSearchRespVO> searchMeetingKnowledge(
            @Valid @RequestBody ChatGptMeetingRagSearchReqVO reqVO) {
        return success(chatGptMeetingService.ragSearch(reqVO));
    }

}
