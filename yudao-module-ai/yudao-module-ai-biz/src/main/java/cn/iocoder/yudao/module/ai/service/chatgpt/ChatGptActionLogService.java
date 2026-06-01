package cn.iocoder.yudao.module.ai.service.chatgpt;

import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptActionLogCreateReqDTO;

public interface ChatGptActionLogService {

    void createLog(ChatGptActionLogCreateReqDTO reqDTO);

}
