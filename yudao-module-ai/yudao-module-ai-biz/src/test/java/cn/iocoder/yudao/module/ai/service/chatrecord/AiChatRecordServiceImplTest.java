package cn.iocoder.yudao.module.ai.service.chatrecord;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatConversationPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationKnowledgeBaseRefDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatMessageDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatCitationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatConversationMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiChatMessageMapper;
import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import cn.iocoder.yudao.module.ai.service.rag.config.AiRagEngineConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_CONVERSATION_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatRecordServiceImplTest {

    @Mock
    private AiChatConversationMapper chatConversationMapper;
    @Mock
    private AiChatMessageMapper chatMessageMapper;
    @Mock
    private AiChatCitationMapper chatCitationMapper;
    @Mock
    private AiRagEngineConfigService ragEngineConfigService;

    private AiChatRecordServiceImpl chatRecordService;

    @BeforeEach
    void setUp() {
        AiUserContextHolder.setUserContext(1L, 100L, 20L);
        chatRecordService = new AiChatRecordServiceImpl(chatConversationMapper, chatMessageMapper, chatCitationMapper,
                ragEngineConfigService);
    }

    @AfterEach
    void tearDown() {
        AiUserContextHolder.clear();
    }

    @Test
    void getConversationPageShouldFilterCurrentUserScope() {
        AiChatConversationPageReqVO reqVO = new AiChatConversationPageReqVO();
        reqVO.setKnowledgeBaseId(10L);
        when(chatConversationMapper.selectPage(reqVO, 1L, 20L, 100L, false))
                .thenReturn(new PageResult<>(List.of(AiChatConversationDO.builder().id(500L).build()), 1L));

        PageResult<AiChatConversationDO> result = chatRecordService.getConversationPage(reqVO);

        assertEquals(1, result.getList().size());
        verify(chatConversationMapper).selectPage(reqVO, 1L, 20L, 100L, false);
    }

    @Test
    void getConversationPageShouldAllowAdminScope() {
        AiUserContextHolder.setUserContext(1L, 999L, 88L, true);
        AiChatConversationPageReqVO reqVO = new AiChatConversationPageReqVO();
        reqVO.setKnowledgeBaseId(10L);
        when(chatConversationMapper.selectPage(reqVO, 1L, 88L, 999L, true))
                .thenReturn(new PageResult<>(List.of(AiChatConversationDO.builder().id(501L).build()), 1L));

        PageResult<AiChatConversationDO> result = chatRecordService.getConversationPage(reqVO);

        assertEquals(501L, result.getList().get(0).getId());
        verify(chatConversationMapper).selectPage(reqVO, 1L, 88L, 999L, true);
    }

    @Test
    void getConversationPageShouldFillDisplayKnowledgeBaseForAllKnowledgeConversation() {
        AiChatConversationPageReqVO reqVO = new AiChatConversationPageReqVO();
        AiChatConversationDO conversation = AiChatConversationDO.builder()
                .id(502L)
                .knowledgeBaseId(0L)
                .build();
        AiChatConversationKnowledgeBaseRefDO ref = new AiChatConversationKnowledgeBaseRefDO();
        ref.setConversationId(502L);
        ref.setKnowledgeBaseId(6L);
        ref.setKnowledgeBaseName("n8n");
        when(chatConversationMapper.selectPage(reqVO, 1L, 20L, 100L, false))
                .thenReturn(new PageResult<>(List.of(conversation), 1L));
        when(ragEngineConfigService.isFastGptEngine()).thenReturn(false);
        when(chatCitationMapper.selectKnowledgeBaseRefsByConversationIds(1L, List.of(502L)))
                .thenReturn(List.of(ref));

        PageResult<AiChatConversationDO> result = chatRecordService.getConversationPage(reqVO);

        assertEquals(6L, result.getList().get(0).getDisplayKnowledgeBaseId());
        assertEquals("n8n", result.getList().get(0).getDisplayKnowledgeBaseName());
    }

    @Test
    void getConversationPageShouldDisplayFastGptForAllKnowledgeConversationInFastGptMode() {
        AiChatConversationPageReqVO reqVO = new AiChatConversationPageReqVO();
        AiChatConversationDO conversation = AiChatConversationDO.builder()
                .id(503L)
                .knowledgeBaseId(0L)
                .build();
        when(chatConversationMapper.selectPage(reqVO, 1L, 20L, 100L, false))
                .thenReturn(new PageResult<>(List.of(conversation), 1L));
        when(ragEngineConfigService.isFastGptEngine()).thenReturn(true);

        PageResult<AiChatConversationDO> result = chatRecordService.getConversationPage(reqVO);

        assertEquals("FastGPT", result.getList().get(0).getDisplayKnowledgeBaseName());
    }

    @Test
    void getConversationPageShouldPreferFastGptKnowledgeBaseNameWhenCitationExists() {
        AiChatConversationPageReqVO reqVO = new AiChatConversationPageReqVO();
        AiChatConversationDO conversation = AiChatConversationDO.builder()
                .id(504L)
                .knowledgeBaseId(0L)
                .build();
        AiChatConversationKnowledgeBaseRefDO ref = new AiChatConversationKnowledgeBaseRefDO();
        ref.setConversationId(504L);
        ref.setKnowledgeBaseId(10L);
        ref.setKnowledgeBaseName("FastGPT 开票库");
        when(chatConversationMapper.selectPage(reqVO, 1L, 20L, 100L, false))
                .thenReturn(new PageResult<>(List.of(conversation), 1L));
        when(chatCitationMapper.selectKnowledgeBaseRefsByConversationIds(1L, List.of(504L)))
                .thenReturn(List.of(ref));
        when(ragEngineConfigService.isFastGptEngine()).thenReturn(true);

        PageResult<AiChatConversationDO> result = chatRecordService.getConversationPage(reqVO);

        assertEquals("FastGPT 开票库", result.getList().get(0).getDisplayKnowledgeBaseName());
    }

    @Test
    void getMessageListShouldValidateConversationPermission() {
        when(chatConversationMapper.selectByIdAndTenantIdAndDepartmentIdAndUserId(500L, 1L, 20L, 100L))
                .thenReturn(AiChatConversationDO.builder().id(500L).tenantId(1L).departmentId(20L).userId(100L).build());
        when(chatMessageMapper.selectListByConversationId(500L, 1L))
                .thenReturn(List.of(AiChatMessageDO.builder().id(1000L).role("user").content("hello").build()));

        List<AiChatMessageDO> messages = chatRecordService.getMessageList(500L);

        assertEquals(1, messages.size());
        assertEquals("hello", messages.get(0).getContent());
    }

    @Test
    void getCitationListShouldValidateMessageConversationPermission() {
        when(chatMessageMapper.selectByIdAndTenantId(1001L, 1L))
                .thenReturn(AiChatMessageDO.builder().id(1001L).conversationId(500L).build());
        when(chatConversationMapper.selectByIdAndTenantIdAndDepartmentIdAndUserId(500L, 1L, 20L, 100L))
                .thenReturn(AiChatConversationDO.builder().id(500L).tenantId(1L).departmentId(20L).userId(100L).build());
        when(chatCitationMapper.selectListByMessageId(1001L, 1L))
                .thenReturn(List.of(AiChatCitationDO.builder().id(2000L).documentTitle("制度").contentSnapshot("引用").build()));

        List<AiChatCitationDO> citations = chatRecordService.getCitationList(1001L);

        assertEquals(1, citations.size());
        assertEquals("制度", citations.get(0).getDocumentTitle());
    }

    @Test
    void getMessageListShouldRejectUnauthorizedConversation() {
        ServiceException exception = assertThrows(ServiceException.class, () -> chatRecordService.getMessageList(500L));

        assertEquals(RAG_CONVERSATION_NOT_EXISTS, exception.getCode());
    }

}
