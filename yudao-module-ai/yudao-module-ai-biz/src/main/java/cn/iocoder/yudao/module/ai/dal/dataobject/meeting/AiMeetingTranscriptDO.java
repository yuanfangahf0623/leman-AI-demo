package cn.iocoder.yudao.module.ai.dal.dataobject.meeting;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_meeting_transcript")
public class AiMeetingTranscriptDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long meetingId;

    private String cleanedContent;

    private String content;

}
