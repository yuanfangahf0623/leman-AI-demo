package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiChatQuestionCacheDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * AI 问答问题缓存 Mapper。
 */
@Mapper
public interface AiChatQuestionCacheMapper extends BaseMapper<AiChatQuestionCacheDO> {

    default AiChatQuestionCacheDO selectLatest(Long tenantId, Long departmentId, Long knowledgeBaseId,
                                               String questionHash) {
        return selectOne(Wrappers.lambdaQuery(AiChatQuestionCacheDO.class)
                .eq(AiChatQuestionCacheDO::getTenantId, tenantId)
                .eq(AiChatQuestionCacheDO::getDepartmentId, departmentId)
                .eq(AiChatQuestionCacheDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(AiChatQuestionCacheDO::getQuestionHash, questionHash)
                .eq(AiChatQuestionCacheDO::getStatus, 0)
                .orderByDesc(AiChatQuestionCacheDO::getId)
                .last("LIMIT 1"));
    }

    default int updateHitCount(Long id, Long tenantId, Long departmentId) {
        return update(null, Wrappers.lambdaUpdate(AiChatQuestionCacheDO.class)
                .setSql("hit_count = hit_count + 1")
                .set(AiChatQuestionCacheDO::getLastHitTime, LocalDateTime.now())
                .eq(AiChatQuestionCacheDO::getId, id)
                .eq(AiChatQuestionCacheDO::getTenantId, tenantId)
                .eq(AiChatQuestionCacheDO::getDepartmentId, departmentId));
    }

    default int updateAnswer(Long id, Long tenantId, Long departmentId, String question, String normalizedQuestion,
                             String answer, String model, Integer promptTokens, Integer completionTokens,
                             Integer totalTokens, Long latencyMs, String citationSnapshotJson) {
        return update(null, Wrappers.lambdaUpdate(AiChatQuestionCacheDO.class)
                .set(AiChatQuestionCacheDO::getQuestion, question)
                .set(AiChatQuestionCacheDO::getNormalizedQuestion, normalizedQuestion)
                .set(AiChatQuestionCacheDO::getAnswer, answer)
                .set(AiChatQuestionCacheDO::getModel, model)
                .set(AiChatQuestionCacheDO::getPromptTokens, promptTokens == null ? 0 : promptTokens)
                .set(AiChatQuestionCacheDO::getCompletionTokens, completionTokens == null ? 0 : completionTokens)
                .set(AiChatQuestionCacheDO::getTotalTokens, totalTokens == null ? 0 : totalTokens)
                .set(AiChatQuestionCacheDO::getLatencyMs, latencyMs == null ? 0L : latencyMs)
                .set(AiChatQuestionCacheDO::getCitationSnapshotJson, citationSnapshotJson)
                .eq(AiChatQuestionCacheDO::getId, id)
                .eq(AiChatQuestionCacheDO::getTenantId, tenantId)
                .eq(AiChatQuestionCacheDO::getDepartmentId, departmentId));
    }

}
