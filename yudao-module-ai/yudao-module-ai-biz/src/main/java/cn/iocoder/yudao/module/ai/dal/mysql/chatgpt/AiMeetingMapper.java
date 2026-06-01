package cn.iocoder.yudao.module.ai.dal.mysql.chatgpt;

import cn.iocoder.yudao.module.ai.dal.dataobject.meeting.AiMeetingDO;
import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptMeetingSearchQueryDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

}
