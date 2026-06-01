package cn.iocoder.yudao.module.ai.dal.dataobject.meeting;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("ai_meeting_action_item")
public class AiMeetingActionItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long meetingId;

    private String task;

    private String owner;

    private LocalDate deadline;

    private String deadlineText;

    private String priority;

    private String sourceQuote;

}
