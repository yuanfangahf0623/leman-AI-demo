package cn.iocoder.yudao.module.ai.service.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;

import java.util.List;

/**
 * RAG Prompt 构建结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptBuildResult {

    public static final String STATUS_NORMAL = "normal";
    public static final String STATUS_NO_CONTEXT = "no-context";

    /**
     * normal 或 no-context，供业务层快速识别是否有可用上下文。
     */
    private String status;

    private String systemPrompt;

    private String userPrompt;

    /**
     * 实际拼接进 Prompt 的上下文，便于后续审计和测试。
     */
    private String context;

    /**
     * 当前上下文的估算 token 数。
     */
    private Integer estimatedContextTokens;

    private String debugInfo;

    /**
     * 实际进入 Prompt 上下文的命中片段，用于保存 citations。
     */
    private List<KnowledgeHit> knowledgeHits;

    public boolean isNoContext() {
        return STATUS_NO_CONTEXT.equals(status);
    }

}
