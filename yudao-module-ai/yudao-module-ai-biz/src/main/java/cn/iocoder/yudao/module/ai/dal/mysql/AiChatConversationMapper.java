package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.chat.vo.AiChatConversationPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatConversationDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * AI 问答会话 Mapper。
 */
@Mapper
public interface AiChatConversationMapper extends BaseMapper<AiChatConversationDO> {

    default AiChatConversationDO selectByIdAndTenantIdAndDepartmentIdAndUserId(Long id, Long tenantId,
                                                                                Long departmentId, Long userId) {
        return selectOne(Wrappers.lambdaQuery(AiChatConversationDO.class)
                .eq(AiChatConversationDO::getId, id)
                .eq(AiChatConversationDO::getTenantId, tenantId)
                .eq(AiChatConversationDO::getDepartmentId, departmentId)
                .eq(AiChatConversationDO::getUserId, userId));
    }

    default AiChatConversationDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(AiChatConversationDO.class)
                .eq(AiChatConversationDO::getId, id)
                .eq(AiChatConversationDO::getTenantId, tenantId));
    }

    default PageResult<AiChatConversationDO> selectPage(AiChatConversationPageReqVO reqVO, Long tenantId,
                                                        Long departmentId, Long userId, boolean admin) {
        IPage<AiChatConversationDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(AiChatConversationDO.class)
                        .eq(AiChatConversationDO::getTenantId, tenantId)
                        .eq(!admin, AiChatConversationDO::getDepartmentId, departmentId)
                        .eq(!admin, AiChatConversationDO::getUserId, userId)
                        .eq(reqVO.getKnowledgeBaseId() != null, AiChatConversationDO::getKnowledgeBaseId,
                                reqVO.getKnowledgeBaseId())
                        .orderByDesc(AiChatConversationDO::getLastMessageTime)
                        .orderByDesc(AiChatConversationDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    default int updateLastMessageTime(Long id, Long tenantId, Long departmentId, LocalDateTime lastMessageTime) {
        return update(null, Wrappers.lambdaUpdate(AiChatConversationDO.class)
                .set(AiChatConversationDO::getLastMessageTime, lastMessageTime)
                .eq(AiChatConversationDO::getId, id)
                .eq(AiChatConversationDO::getTenantId, tenantId)
                .eq(AiChatConversationDO::getDepartmentId, departmentId));
    }

}
