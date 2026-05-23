package cn.iocoder.yudao.module.ai.service.rag.fastgpt;

import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.rag.RagChatCitation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FastGptRagResult {

    private AiChatModelResponse modelResponse;

    @Builder.Default
    private List<RagChatCitation> citations = Collections.emptyList();

    private String debugInfo;

}
