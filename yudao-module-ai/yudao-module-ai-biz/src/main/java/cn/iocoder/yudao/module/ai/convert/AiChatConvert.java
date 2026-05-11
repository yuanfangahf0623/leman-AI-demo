package cn.iocoder.yudao.module.ai.convert;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatCitationRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatCompletionCitationRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatCompletionReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatCompletionRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatConversationRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatMessageRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatMessageDO;
import cn.iocoder.yudao.module.ai.service.rag.RagChatCitation;
import cn.iocoder.yudao.module.ai.service.rag.RagChatRequest;
import cn.iocoder.yudao.module.ai.service.rag.RagChatResponse;

import java.util.Collections;
import java.util.List;

/**
 * AI 问答 Convert。
 */
public class AiChatConvert {

    public static final AiChatConvert INSTANCE = new AiChatConvert();

    public RagChatRequest convert(AiChatCompletionReqVO bean) {
        if (bean == null) {
            return null;
        }
        return RagChatRequest.builder()
                .knowledgeBaseId(bean.getKnowledgeBaseId())
                .conversationId(bean.getConversationId())
                .question(bean.getQuestion())
                .topK(bean.getTopK())
                .scoreThreshold(bean.getScoreThreshold())
                .build();
    }

    public AiChatCompletionRespVO convert(RagChatResponse bean) {
        if (bean == null) {
            return null;
        }
        AiChatCompletionRespVO result = new AiChatCompletionRespVO();
        result.setConversationId(bean.getConversationId());
        result.setUserMessageId(bean.getUserMessageId());
        result.setAssistantMessageId(bean.getAssistantMessageId());
        result.setAnswer(bean.getAnswer());
        result.setNoContext(bean.getNoContext());
        result.setDebugInfo(bean.getDebugInfo());
        result.setCitations(convertCompletionCitationList(bean.getCitations()));
        return result;
    }

    private List<AiChatCompletionCitationRespVO> convertCompletionCitationList(List<RagChatCitation> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::convert).toList();
    }

    private AiChatCompletionCitationRespVO convert(RagChatCitation bean) {
        AiChatCompletionCitationRespVO result = new AiChatCompletionCitationRespVO();
        result.setDocumentId(bean.getDocumentId());
        result.setChunkId(bean.getChunkId());
        result.setChunkNo(bean.getChunkNo());
        result.setDocumentTitle(bean.getDocumentTitle());
        result.setScore(bean.getScore());
        result.setQuoteText(bean.getQuoteText());
        return result;
    }

    public PageResult<AiChatConversationRespVO> convertConversationPage(PageResult<AiChatConversationDO> page) {
        if (page == null) {
            return null;
        }
        return new PageResult<>(page.getList().stream().map(this::convert).toList(), page.getTotal());
    }

    public List<AiChatMessageRespVO> convertMessageList(List<AiChatMessageDO> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::convert).toList();
    }

    public List<AiChatCitationRespVO> convertCitationList(List<AiChatCitationDO> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::convert).toList();
    }

    private AiChatConversationRespVO convert(AiChatConversationDO bean) {
        AiChatConversationRespVO result = new AiChatConversationRespVO();
        result.setId(bean.getId());
        result.setKnowledgeBaseId(bean.getKnowledgeBaseId());
        result.setUserId(bean.getUserId());
        result.setDepartmentId(bean.getDepartmentId());
        result.setTitle(bean.getTitle());
        result.setStatus(bean.getStatus());
        result.setPinned(bean.getPinned());
        result.setPinnedTime(bean.getPinnedTime());
        result.setLastMessageTime(bean.getLastMessageTime());
        result.setCreateTime(bean.getCreateTime());
        return result;
    }

    private AiChatMessageRespVO convert(AiChatMessageDO bean) {
        AiChatMessageRespVO result = new AiChatMessageRespVO();
        result.setId(bean.getId());
        result.setConversationId(bean.getConversationId());
        result.setRole(bean.getRole());
        result.setContent(bean.getContent());
        result.setModel(bean.getModel());
        result.setPromptTokens(bean.getPromptTokens());
        result.setCompletionTokens(bean.getCompletionTokens());
        result.setTotalTokens(bean.getTotalTokens());
        result.setLatencyMs(bean.getLatencyMs());
        result.setStatus(bean.getStatus());
        result.setErrorMessage(bean.getErrorMessage());
        result.setCreateTime(bean.getCreateTime());
        return result;
    }

    private AiChatCitationRespVO convert(AiChatCitationDO bean) {
        AiChatCitationRespVO result = new AiChatCitationRespVO();
        result.setId(bean.getId());
        result.setMessageId(bean.getMessageId());
        result.setKnowledgeBaseId(bean.getKnowledgeBaseId());
        result.setDocumentId(bean.getDocumentId());
        result.setChunkId(bean.getChunkId());
        result.setDocumentTitle(bean.getDocumentTitle());
        result.setScore(bean.getScore());
        result.setSortOrder(bean.getSortOrder());
        result.setContentSnapshot(bean.getContentSnapshot() != null ? bean.getContentSnapshot() : bean.getQuoteText());
        result.setCreateTime(bean.getCreateTime());
        return result;
    }

}
