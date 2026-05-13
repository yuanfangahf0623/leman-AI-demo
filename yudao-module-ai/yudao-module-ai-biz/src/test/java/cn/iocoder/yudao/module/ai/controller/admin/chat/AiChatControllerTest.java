package cn.iocoder.yudao.module.ai.controller.admin.chat;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatCompletionReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatCompletionRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatConversationPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatConversationRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatMessageRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatCitationRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatMessageDO;
import cn.iocoder.yudao.module.ai.service.chatrecord.AiChatRecordService;
import cn.iocoder.yudao.module.ai.service.rag.RagChatCitation;
import cn.iocoder.yudao.module.ai.service.rag.RagChatRequest;
import cn.iocoder.yudao.module.ai.service.rag.RagChatResponse;
import cn.iocoder.yudao.module.ai.service.rag.RagService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_STREAM_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatControllerTest {

    @Test
    void completionsShouldCallRagServiceAndReturnAnswerWithCitations() {
        RagService ragService = mock(RagService.class);
        AiChatController controller = new AiChatController(ragService, mock(AiChatRecordService.class));
        AiChatCompletionReqVO reqVO = new AiChatCompletionReqVO();
        reqVO.setKnowledgeBaseId(1001L);
        reqVO.setConversationId(3001L);
        reqVO.setQuestion("A 类设备点检周期是多久？");
        reqVO.setStream(false);
        when(ragService.chat(org.mockito.ArgumentMatchers.any(RagChatRequest.class))).thenReturn(RagChatResponse.builder()
                .conversationId(3001L)
                .userMessageId(4001L)
                .assistantMessageId(4002L)
                .answer("A 类设备点检周期为每日一次。")
                .noContext(false)
                .citations(List.of(RagChatCitation.builder()
                        .documentId(5001L)
                        .chunkId(6001L)
                        .chunkNo(2)
                        .documentTitle("设备点检制度")
                        .score(0.92D)
                        .quoteText("A 类设备每日点检。")
                        .build()))
                .build());

        CommonResult<AiChatCompletionRespVO> result = controller.completions(reqVO);

        assertEquals(CommonResult.SUCCESS_CODE, result.getCode());
        assertEquals("A 类设备点检周期为每日一次。", result.getData().getAnswer());
        assertEquals(3001L, result.getData().getConversationId());
        assertEquals(1, result.getData().getCitations().size());
        assertEquals("设备点检制度", result.getData().getCitations().get(0).getDocumentTitle());

        ArgumentCaptor<RagChatRequest> requestCaptor = ArgumentCaptor.forClass(RagChatRequest.class);
        verify(ragService).chat(requestCaptor.capture());
        assertEquals(1001L, requestCaptor.getValue().getKnowledgeBaseId());
        assertEquals(3001L, requestCaptor.getValue().getConversationId());
        assertEquals("A 类设备点检周期是多久？", requestCaptor.getValue().getQuestion());
    }

    @Test
    void completionsShouldRejectStreamRequest() {
        RagService ragService = mock(RagService.class);
        AiChatController controller = new AiChatController(ragService, mock(AiChatRecordService.class));
        AiChatCompletionReqVO reqVO = new AiChatCompletionReqVO();
        reqVO.setKnowledgeBaseId(1001L);
        reqVO.setQuestion("A 类设备点检周期是多久？");
        reqVO.setStream(true);

        ServiceException exception = assertThrows(ServiceException.class, () -> controller.completions(reqVO));

        assertEquals(RAG_STREAM_NOT_SUPPORTED, exception.getCode());
    }

    @Test
    void getConversationPageShouldReturnPage() {
        RagService ragService = mock(RagService.class);
        AiChatRecordService chatRecordService = mock(AiChatRecordService.class);
        AiChatController controller = new AiChatController(ragService, chatRecordService);
        AiChatConversationPageReqVO reqVO = new AiChatConversationPageReqVO();
        reqVO.setKnowledgeBaseId(1001L);
        when(chatRecordService.getConversationPage(reqVO)).thenReturn(new PageResult<>(List.of(
                AiChatConversationDO.builder().id(3001L).knowledgeBaseId(0L).displayKnowledgeBaseId(6L)
                        .displayKnowledgeBaseName("n8n").title("A 类设备点检周期").build()), 1L));

        CommonResult<PageResult<AiChatConversationRespVO>> result = controller.getConversationPage(reqVO);

        assertEquals(1L, result.getData().getTotal());
        assertEquals(3001L, result.getData().getList().get(0).getId());
        assertEquals(6L, result.getData().getList().get(0).getDisplayKnowledgeBaseId());
        assertEquals("n8n", result.getData().getList().get(0).getDisplayKnowledgeBaseName());
        assertEquals("A 类设备点检周期", result.getData().getList().get(0).getTitle());
    }

    @Test
    void getMessageListShouldReturnModelTokenAndLatency() {
        RagService ragService = mock(RagService.class);
        AiChatRecordService chatRecordService = mock(AiChatRecordService.class);
        AiChatController controller = new AiChatController(ragService, chatRecordService);
        when(chatRecordService.getMessageList(3001L)).thenReturn(List.of(AiChatMessageDO.builder()
                .id(4002L)
                .conversationId(3001L)
                .role("assistant")
                .content("A 类设备每日点检。")
                .model("unit-test-chat-model")
                .promptTokens(10)
                .completionTokens(5)
                .totalTokens(15)
                .latencyMs(123L)
                .build()));

        CommonResult<List<AiChatMessageRespVO>> result = controller.getMessageList(3001L);

        assertEquals("assistant", result.getData().get(0).getRole());
        assertEquals("unit-test-chat-model", result.getData().get(0).getModel());
        assertEquals(15, result.getData().get(0).getTotalTokens());
        assertEquals(123L, result.getData().get(0).getLatencyMs());
    }

    @Test
    void getCitationListShouldReturnDocumentTitleAndSnapshot() {
        RagService ragService = mock(RagService.class);
        AiChatRecordService chatRecordService = mock(AiChatRecordService.class);
        AiChatController controller = new AiChatController(ragService, chatRecordService);
        when(chatRecordService.getCitationList(4002L)).thenReturn(List.of(AiChatCitationDO.builder()
                .id(5001L)
                .messageId(4002L)
                .documentTitle("设备点检制度")
                .score(new BigDecimal("0.920000"))
                .contentSnapshot("A 类设备每日点检。")
                .build()));

        CommonResult<List<AiChatCitationRespVO>> result = controller.getCitationList(4002L);

        assertEquals("设备点检制度", result.getData().get(0).getDocumentTitle());
        assertEquals(new BigDecimal("0.920000"), result.getData().get(0).getScore());
        assertEquals("A 类设备每日点检。", result.getData().get(0).getContentSnapshot());
    }

}
