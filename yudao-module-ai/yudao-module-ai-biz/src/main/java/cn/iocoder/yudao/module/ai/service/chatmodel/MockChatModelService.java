package cn.iocoder.yudao.module.ai.service.chatmodel;

import cn.iocoder.yudao.module.ai.enums.ChatMessageRoleEnum;

import java.util.Collections;
import java.util.List;

/**
 * 本地开发和测试使用的 Mock 聊天模型服务。
 */
public class MockChatModelService implements AiChatModelService {

    private static final String DEFAULT_MODEL = "mock-chat-model";
    private static final String FINISH_REASON_STOP = "stop";
    private static final String EMPTY_USER_MESSAGE = "";

    @Override
    public AiChatModelResponse chat(AiChatModelRequest request) {
        String userMessage = findLastUserMessage(request);
        String content = userMessage.isBlank() ? "Mock answer." : "Mock answer: " + userMessage;
        int promptTokens = estimatePromptTokens(request);
        int completionTokens = estimateTokens(content);
        return AiChatModelResponse.builder()
                .model(resolveModel(request))
                .content(content)
                .finishReason(FINISH_REASON_STOP)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .metadata(Collections.emptyMap())
                .build();
    }

    private String resolveModel(AiChatModelRequest request) {
        if (request == null || request.getModel() == null || request.getModel().isBlank()) {
            return DEFAULT_MODEL;
        }
        return request.getModel().trim();
    }

    private String findLastUserMessage(AiChatModelRequest request) {
        if (request == null) {
            return EMPTY_USER_MESSAGE;
        }
        if (request.getUserPrompt() != null && !request.getUserPrompt().isBlank()) {
            return request.getUserPrompt();
        }
        List<AiChatModelMessage> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            return EMPTY_USER_MESSAGE;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiChatModelMessage message = messages.get(i);
            if (message != null && ChatMessageRoleEnum.USER.getCode().equals(message.getRole())) {
                return message.getContent() == null ? EMPTY_USER_MESSAGE : message.getContent();
            }
        }
        return EMPTY_USER_MESSAGE;
    }

    private int estimateTokens(List<AiChatModelMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        for (AiChatModelMessage message : messages) {
            tokens += estimateTokens(message == null ? null : message.getContent());
        }
        return tokens;
    }

    private int estimatePromptTokens(AiChatModelRequest request) {
        if (request == null) {
            return 0;
        }
        int tokens = estimateTokens(request.getMessages());
        tokens += estimateTokens(request.getSystemPrompt());
        tokens += estimateTokens(request.getUserPrompt());
        return tokens;
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (content.length() + 3) / 4);
    }

}
