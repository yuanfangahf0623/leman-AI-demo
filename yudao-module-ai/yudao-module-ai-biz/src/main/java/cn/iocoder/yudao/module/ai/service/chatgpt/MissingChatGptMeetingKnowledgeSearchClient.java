package cn.iocoder.yudao.module.ai.service.chatgpt;

import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptMeetingKnowledgeSearchRequestDTO;
import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptMeetingKnowledgeSearchResultDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnMissingBean(ChatGptMeetingKnowledgeSearchClient.class)
public class MissingChatGptMeetingKnowledgeSearchClient implements ChatGptMeetingKnowledgeSearchClient {

    @Override
    public List<ChatGptMeetingKnowledgeSearchResultDTO> search(ChatGptMeetingKnowledgeSearchRequestDTO reqDTO) {
        throw new IllegalStateException("ChatGptMeetingKnowledgeSearchClient implementation is missing");
    }

}
