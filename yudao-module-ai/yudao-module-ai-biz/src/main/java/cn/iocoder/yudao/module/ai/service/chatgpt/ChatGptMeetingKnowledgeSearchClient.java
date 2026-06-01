package cn.iocoder.yudao.module.ai.service.chatgpt;

import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptMeetingKnowledgeSearchRequestDTO;
import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptMeetingKnowledgeSearchResultDTO;

import java.util.List;

public interface ChatGptMeetingKnowledgeSearchClient {

    /**
     * Search meeting knowledge through the existing knowledge retrieval implementation.
     */
    List<ChatGptMeetingKnowledgeSearchResultDTO> search(ChatGptMeetingKnowledgeSearchRequestDTO reqDTO);

}
