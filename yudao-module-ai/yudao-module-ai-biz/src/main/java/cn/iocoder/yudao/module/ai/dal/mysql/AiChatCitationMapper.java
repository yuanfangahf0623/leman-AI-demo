package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationKnowledgeBaseRefDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatCitationDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * AI 引用来源 Mapper。
 */
@Mapper
public interface AiChatCitationMapper extends BaseMapper<AiChatCitationDO> {

    default List<AiChatCitationDO> selectListByMessageId(Long messageId, Long tenantId) {
        return selectList(Wrappers.lambdaQuery(AiChatCitationDO.class)
                .eq(AiChatCitationDO::getMessageId, messageId)
                .eq(AiChatCitationDO::getTenantId, tenantId)
                .orderByAsc(AiChatCitationDO::getSortOrder)
                .orderByAsc(AiChatCitationDO::getId));
    }

    @Select("""
            <script>
            SELECT ref.conversation_id AS conversationId,
                   ref.knowledge_base_id AS knowledgeBaseId,
                   kb.name AS knowledgeBaseName
            FROM (
                SELECT m.conversation_id, c.knowledge_base_id, m.id AS message_id, c.sort_order, c.id AS citation_id
                FROM ai_chat_message m
                INNER JOIN ai_chat_citation c ON c.message_id = m.id
                    AND c.tenant_id = m.tenant_id
                    AND c.deleted = 0
                WHERE m.tenant_id = #{tenantId}
                  AND m.deleted = 0
                  AND m.conversation_id IN
                  <foreach collection="conversationIds" item="conversationId" open="(" separator="," close=")">
                      #{conversationId}
                  </foreach>
            ) ref
            INNER JOIN ai_knowledge_base kb ON kb.id = ref.knowledge_base_id
                AND kb.tenant_id = #{tenantId}
                AND kb.deleted = 0
            ORDER BY ref.conversation_id ASC, ref.message_id DESC, ref.sort_order ASC, ref.citation_id ASC
            </script>
            """)
    List<AiChatConversationKnowledgeBaseRefDO> selectKnowledgeBaseRefsByConversationIds(
            @Param("tenantId") Long tenantId,
            @Param("conversationIds") Collection<Long> conversationIds);

}
