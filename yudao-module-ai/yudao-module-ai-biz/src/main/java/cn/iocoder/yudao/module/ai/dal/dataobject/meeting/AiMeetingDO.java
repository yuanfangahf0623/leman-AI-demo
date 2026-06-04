package cn.iocoder.yudao.module.ai.dal.dataobject.meeting;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_meeting")
public class AiMeetingDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String subject;

    private String summary;

    private String organizerName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String sourceType;

    private String sourceMeetingId;

    private String sourceOnlineMeetingId;

    private Long knowledgeBaseId;

    private Long transcriptDocumentId;

    private Long minutesDocumentId;

    private String minutesStatus;

    private String transcriptStatus;

    private String syncStatus;

    private String errorMessage;

    private Boolean chatgptVisible;

    private String sensitivityLevel;

    private String projectCode;

    private Boolean deleted;

}
