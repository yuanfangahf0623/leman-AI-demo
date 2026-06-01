package cn.iocoder.yudao.module.ai.dal.dataobject.chatgpt;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chatgpt_action_log")
public class AiChatGptActionLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String actionName;

    private String requestId;

    private String callerType;

    private String callerIdentity;

    private Long meetingId;

    private Long knowledgeBaseId;

    private String queryText;

    private Boolean success;

    private String errorCode;

    private LocalDateTime createTime;

}
