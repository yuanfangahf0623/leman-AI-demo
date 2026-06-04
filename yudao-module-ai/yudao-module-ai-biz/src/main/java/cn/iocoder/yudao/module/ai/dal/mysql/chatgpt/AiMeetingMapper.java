package cn.iocoder.yudao.module.ai.dal.mysql.chatgpt;

import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingDO;
import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptMeetingSearchQueryDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface AiMeetingMapper extends BaseMapper<AiMeetingDO> {

    default List<AiMeetingDO> selectChatGptVisibleList(ChatGptMeetingSearchQueryDTO queryDTO, Long tenantId,
                                                       Collection<String> allowedSensitivityLevels, int limit) {
        LambdaQueryWrapper<AiMeetingDO> query = new LambdaQueryWrapper<AiMeetingDO>()
                .eq(AiMeetingDO::getTenantId, tenantId)
                .eq(AiMeetingDO::getChatgptVisible, true)
                .eq(AiMeetingDO::getDeleted, false)
                .in(AiMeetingDO::getSensitivityLevel, allowedSensitivityLevels)
                .orderByDesc(AiMeetingDO::getStartTime)
                .last("LIMIT " + limit);
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            query.and(wrapper -> wrapper.like(AiMeetingDO::getSubject, queryDTO.getKeyword())
                    .or().like(AiMeetingDO::getSummary, queryDTO.getKeyword())
                    .or().like(AiMeetingDO::getOrganizerName, queryDTO.getKeyword()));
        }
        if (queryDTO.getStartDate() != null) {
            query.ge(AiMeetingDO::getStartTime, queryDTO.getStartDate().atStartOfDay());
        }
        if (queryDTO.getEndDate() != null) {
            query.le(AiMeetingDO::getStartTime, queryDTO.getEndDate().atTime(LocalTime.MAX));
        }
        if (StringUtils.hasText(queryDTO.getOrganizer())) {
            query.like(AiMeetingDO::getOrganizerName, queryDTO.getOrganizer());
        }
        if (StringUtils.hasText(queryDTO.getProjectCode())) {
            query.eq(AiMeetingDO::getProjectCode, queryDTO.getProjectCode());
        }
        return selectList(query);
    }

    default AiMeetingDO selectAccessibleById(Long id, Long tenantId, Collection<String> allowedSensitivityLevels) {
        return selectOne(new LambdaQueryWrapper<AiMeetingDO>()
                .eq(AiMeetingDO::getId, id)
                .eq(AiMeetingDO::getTenantId, tenantId)
                .eq(AiMeetingDO::getChatgptVisible, true)
                .eq(AiMeetingDO::getDeleted, false)
                .in(AiMeetingDO::getSensitivityLevel, allowedSensitivityLevels));
    }

    default AiMeetingDO selectBySourceMeetingId(Long tenantId, String sourceType, String sourceMeetingId) {
        return selectOne(new LambdaQueryWrapper<AiMeetingDO>()
                .eq(AiMeetingDO::getTenantId, tenantId)
                .eq(AiMeetingDO::getSourceType, sourceType)
                .eq(AiMeetingDO::getSourceMeetingId, sourceMeetingId)
                .eq(AiMeetingDO::getDeleted, false)
                .last("LIMIT 1"));
    }

    default int updateSyncByIdAndTenantId(AiMeetingDO meeting, Long tenantId) {
        return update(null, new LambdaUpdateWrapper<AiMeetingDO>()
                .set(AiMeetingDO::getSubject, meeting.getSubject())
                .set(AiMeetingDO::getSummary, meeting.getSummary())
                .set(AiMeetingDO::getOrganizerName, meeting.getOrganizerName())
                .set(AiMeetingDO::getStartTime, meeting.getStartTime())
                .set(AiMeetingDO::getEndTime, meeting.getEndTime())
                .set(AiMeetingDO::getSourceOnlineMeetingId, meeting.getSourceOnlineMeetingId())
                .set(AiMeetingDO::getKnowledgeBaseId, meeting.getKnowledgeBaseId())
                .set(AiMeetingDO::getMinutesStatus, meeting.getMinutesStatus())
                .set(AiMeetingDO::getTranscriptStatus, meeting.getTranscriptStatus())
                .set(AiMeetingDO::getSyncStatus, meeting.getSyncStatus())
                .set(AiMeetingDO::getErrorMessage, meeting.getErrorMessage())
                .set(AiMeetingDO::getChatgptVisible, meeting.getChatgptVisible())
                .set(AiMeetingDO::getSensitivityLevel, meeting.getSensitivityLevel())
                .set(AiMeetingDO::getProjectCode, meeting.getProjectCode())
                .eq(AiMeetingDO::getId, meeting.getId())
                .eq(AiMeetingDO::getTenantId, tenantId));
    }

    default int updateDocumentIdsByIdAndTenantId(Long id, Long tenantId, Long transcriptDocumentId,
                                                 Long minutesDocumentId) {
        return update(null, new LambdaUpdateWrapper<AiMeetingDO>()
                .set(AiMeetingDO::getTranscriptDocumentId, transcriptDocumentId)
                .set(AiMeetingDO::getMinutesDocumentId, minutesDocumentId)
                .eq(AiMeetingDO::getId, id)
                .eq(AiMeetingDO::getTenantId, tenantId));
    }

}
