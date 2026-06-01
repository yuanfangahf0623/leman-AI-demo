package cn.iocoder.yudao.module.ai.service.chatgpt;

import cn.iocoder.yudao.module.ai.dal.dataobject.chatgpt.AiChatGptActionLogDO;
import cn.iocoder.yudao.module.ai.dal.mysql.chatgpt.ChatGptActionLogMapper;
import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptActionLogCreateReqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatGptActionLogServiceImpl implements ChatGptActionLogService {

    private static final int MAX_QUERY_TEXT_LENGTH = 1000;

    private final ChatGptActionLogMapper chatGptActionLogMapper;
    private final ChatGptSensitiveMaskService sensitiveMaskService;

    @Override
    public void createLog(ChatGptActionLogCreateReqDTO reqDTO) {
        try {
            AiChatGptActionLogDO logDO = new AiChatGptActionLogDO();
            logDO.setTenantId(reqDTO.getTenantId() == null ? 0L : reqDTO.getTenantId());
            logDO.setActionName(limit(reqDTO.getActionName(), 128));
            logDO.setRequestId(limit(reqDTO.getRequestId(), 128));
            logDO.setCallerType(limit(reqDTO.getCallerType(), 32));
            logDO.setCallerIdentity(limit(reqDTO.getCallerIdentity(), 256));
            logDO.setMeetingId(reqDTO.getMeetingId());
            logDO.setKnowledgeBaseId(reqDTO.getKnowledgeBaseId());
            logDO.setQueryText(limit(sensitiveMaskService.mask(reqDTO.getQueryText()), MAX_QUERY_TEXT_LENGTH));
            logDO.setSuccess(Boolean.TRUE.equals(reqDTO.getSuccess()));
            logDO.setErrorCode(limit(reqDTO.getErrorCode(), 64));
            logDO.setCreateTime(LocalDateTime.now());
            chatGptActionLogMapper.insert(logDO);
        } catch (Exception ex) {
            log.warn("[createLog][ChatGPT action audit log write failed, actionName={}, requestId={}]",
                    reqDTO.getActionName(), reqDTO.getRequestId(), ex);
        }
    }

    private static String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

}
