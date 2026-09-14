package cn.iocoder.yudao.module.ai.dal.mysql;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiDifyConversationMapper {
    @Select("SELECT external_id FROM ai_dify_conversation WHERE tenant_id=#{tenant} AND knowledge_base_id=#{kb} AND user_id=#{user} AND conversation_id=#{conversation}")
    String find(@Param("tenant") Long tenant, @Param("kb") Long kb, @Param("user") Long user,
                @Param("conversation") Long conversation);

    @Insert("INSERT INTO ai_dify_conversation(tenant_id,knowledge_base_id,user_id,conversation_id,external_id) VALUES(#{tenant},#{kb},#{user},#{conversation},#{external}) ON DUPLICATE KEY UPDATE external_id=VALUES(external_id)")
    int save(@Param("tenant") Long tenant, @Param("kb") Long kb, @Param("user") Long user,
             @Param("conversation") Long conversation, @Param("external") String external);
}
