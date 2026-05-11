package cn.iocoder.yudao.module.ai.service.rag;

import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RAG 问答 Prompt 构建器。
 */
@Component
public class PromptBuilder {

    private static final int DEFAULT_MAX_CONTEXT_TOKENS = 6000;
    private static final int TOKEN_CHAR_RATIO = 4;
    private static final String UNKNOWN_DOCUMENT_TITLE = "未知文档";
    private static final String UNKNOWN_CHUNK_NO = "未知";
    private static final String TRUNCATED_MARKER = "\n[内容已因上下文长度限制截断]";

    public static final String NO_CONTEXT = PromptBuildResult.STATUS_NO_CONTEXT;

    private static final String SYSTEM_PROMPT = """
            你是企业内部知识库助手。
            请严格基于给定的知识片段回答问题。
            如果知识片段中没有答案，请回答“根据当前知识库资料无法确认”。
            不要编造不存在的制度、数据、流程或结论。""";

    private final int maxContextTokens;

    public PromptBuilder(AiProperties aiProperties) {
        this.maxContextTokens = resolveMaxContextTokens(aiProperties);
    }

    public PromptBuildResult build(String question, List<KnowledgeHit> knowledgeHits) {
        String safeQuestion = question == null ? "" : question.trim();
        int originalHitCount = knowledgeHits == null ? 0 : knowledgeHits.size();
        List<KnowledgeHit> effectiveHits = selectEffectiveHits(knowledgeHits);
        if (effectiveHits.isEmpty()) {
            return buildNoContextPrompt(safeQuestion, originalHitCount);
        }

        ContextBuildResult contextBuildResult = buildContext(effectiveHits);
        if (contextBuildResult.context().isBlank()) {
            return buildNoContextPrompt(safeQuestion, originalHitCount);
        }
        String userPrompt = buildUserPrompt(safeQuestion, contextBuildResult.context());
        return PromptBuildResult.builder()
                .status(PromptBuildResult.STATUS_NORMAL)
                .systemPrompt(SYSTEM_PROMPT)
                .userPrompt(userPrompt)
                .context(contextBuildResult.context())
                .estimatedContextTokens(estimateTokens(contextBuildResult.context()))
                .debugInfo(buildDebugInfo(safeQuestion, originalHitCount, effectiveHits, contextBuildResult,
                        SYSTEM_PROMPT, userPrompt, PromptBuildResult.STATUS_NORMAL))
                .knowledgeHits(contextBuildResult.knowledgeHits())
                .build();
    }

    private List<KnowledgeHit> selectEffectiveHits(List<KnowledgeHit> knowledgeHits) {
        if (knowledgeHits == null || knowledgeHits.isEmpty()) {
            return List.of();
        }
        List<KnowledgeHit> effectiveHits = new ArrayList<>();
        for (KnowledgeHit hit : knowledgeHits) {
            if (hit != null && hit.getContent() != null && !hit.getContent().isBlank()) {
                effectiveHits.add(hit);
            }
        }
        return effectiveHits;
    }

    private ContextBuildResult buildContext(List<KnowledgeHit> effectiveHits) {
        StringBuilder context = new StringBuilder();
        List<KnowledgeHit> usedHits = new ArrayList<>();
        int maxContextChars = maxContextTokens * TOKEN_CHAR_RATIO;
        boolean truncated = false;
        for (int i = 0; i < effectiveHits.size(); i++) {
            if (context.length() >= maxContextChars) {
                truncated = true;
                break;
            }
            String fragment = buildContextFragment(i + 1, effectiveHits.get(i));
            truncated = appendWithLimit(context, fragment, maxContextChars) || truncated;
            usedHits.add(effectiveHits.get(i));
            if (truncated) {
                break;
            }
        }
        return new ContextBuildResult(context.toString().trim(), usedHits, truncated, maxContextChars);
    }

    private String buildContextFragment(int index, KnowledgeHit hit) {
        return """
                
                【来源 %s】
                文档标题：%s
                Chunk 编号：%s
                相似度：%s
                内容：
                %s
                """.formatted(index, normalizeDocumentTitle(hit), normalizeChunkNo(hit), normalizeScore(hit),
                hit.getContent().trim());
    }

    private boolean appendWithLimit(StringBuilder context, String fragment, int maxContextChars) {
        int remainingChars = maxContextChars - context.length();
        if (remainingChars <= 0) {
            return true;
        }
        if (fragment.length() <= remainingChars) {
            context.append(fragment);
            return false;
        }
        int markerLength = Math.min(TRUNCATED_MARKER.length(), remainingChars);
        int contentLength = Math.max(0, remainingChars - markerLength);
        context.append(fragment, 0, contentLength);
        context.append(TRUNCATED_MARKER, 0, markerLength);
        return true;
    }

    private PromptBuildResult buildNoContextPrompt(String question, int originalHitCount) {
        String userPrompt = """
                用户问题：
                %s
                知识片段：
                no-context
                回答要求：
                请回答“根据当前知识库资料无法确认”。
                """.formatted(question);
        ContextBuildResult contextBuildResult = new ContextBuildResult("", List.of(), false,
                maxContextTokens * TOKEN_CHAR_RATIO);
        return PromptBuildResult.builder()
                .status(PromptBuildResult.STATUS_NO_CONTEXT)
                .systemPrompt(SYSTEM_PROMPT)
                .userPrompt(userPrompt)
                .context("")
                .estimatedContextTokens(0)
                .debugInfo(buildDebugInfo(question, originalHitCount, List.of(), contextBuildResult,
                        SYSTEM_PROMPT, userPrompt, PromptBuildResult.STATUS_NO_CONTEXT))
                .knowledgeHits(List.of())
                .build();
    }

    private String buildUserPrompt(String question, String context) {
        return """
                用户问题：
                %s
                知识片段：
                %s
                回答要求：
                先直接回答结论。
                再给出依据。
                如果有来源，请列出来源文档。
                """.formatted(question, context);
    }

    private String buildDebugInfo(String question, int originalHitCount, List<KnowledgeHit> effectiveHits,
                                  ContextBuildResult contextBuildResult, String systemPrompt, String userPrompt,
                                  String status) {
        StringBuilder debug = new StringBuilder();
        debug.append("## RAG Prompt 调试信息\n");
        debug.append("> 说明：以下为可复现的 RAG 构造过程，不包含模型内部思考过程。\n\n");
        debug.append("### 1. 如何选择命中的 KnowledgeHit\n");
        debug.append("- 向量检索返回命中数：").append(originalHitCount).append("\n");
        debug.append("- 过滤规则：丢弃 null 命中、content 为空的命中；保留向量检索返回顺序，不在 PromptBuilder 内重新排序。\n");
        debug.append("- 有效命中数：").append(effectiveHits.size()).append("\n");
        debug.append("- 实际进入 context 的命中数：").append(contextBuildResult.knowledgeHits().size()).append("\n");
        for (int i = 0; i < contextBuildResult.knowledgeHits().size(); i++) {
            KnowledgeHit hit = contextBuildResult.knowledgeHits().get(i);
            debug.append("  - Hit ").append(i + 1)
                    .append("：documentId=").append(hit.getDocumentId())
                    .append("，chunkId=").append(hit.getChunkId())
                    .append("，chunkNo=").append(normalizeChunkNo(hit))
                    .append("，documentTitle=").append(normalizeDocumentTitle(hit))
                    .append("，score=").append(normalizeScore(hit))
                    .append("，contentLength=").append(hit.getContent() == null ? 0 : hit.getContent().length())
                    .append("\n");
        }

        debug.append("\n### 2. 如何拼接 context\n");
        debug.append("- 拼接模板：每个片段按 `【来源 N】/文档标题/Chunk 编号/相似度/内容` 拼接。\n");
        debug.append("- 拼接顺序：沿用进入 PromptBuilder 的命中顺序。\n");
        debug.append("- 当前 context 字符数：").append(contextBuildResult.context().length()).append("\n");
        debug.append("- 当前 context 估算 token：").append(estimateTokens(contextBuildResult.context())).append("\n");

        debug.append("\n### 3. 如何截断超长 context\n");
        debug.append("- maxContextTokens：").append(maxContextTokens).append("\n");
        debug.append("- 字符预算：").append(contextBuildResult.maxContextChars())
                .append("（按 1 token 约 4 字符估算）\n");
        debug.append("- 是否发生截断：").append(contextBuildResult.truncated() ? "是" : "否").append("\n");
        debug.append("- 截断方式：按片段顺序追加，超出预算时保留已追加内容并写入截断标记。\n");

        debug.append("\n### 4. 如何生成 systemPrompt 和 userPrompt\n");
        debug.append("- systemPrompt：固定企业知识库助手约束，要求严格基于知识片段回答，不足时输出固定兜底语。\n");
        debug.append("- userPrompt：由用户问题、context、回答要求三部分组成。\n");

        debug.append("\n### 5. 如何处理 no-context 情况\n");
        debug.append("- 当前状态：").append(status).append("\n");
        debug.append("- 当没有有效命中或 context 为空时，知识片段设置为 `no-context`，并要求回答“根据当前知识库资料无法确认”。\n");

        debug.append("\n### 6. 最终生成的 Prompt 示例\n");
        debug.append("```text\n");
        debug.append("[system]\n").append(systemPrompt).append("\n\n");
        debug.append("[user]\n").append(userPrompt).append("\n");
        debug.append("```\n");
        debug.append("\n### 7. 用户问题\n");
        debug.append(question);
        return debug.toString();
    }

    private String normalizeDocumentTitle(KnowledgeHit hit) {
        if (hit.getDocumentTitle() != null && !hit.getDocumentTitle().isBlank()) {
            return hit.getDocumentTitle().trim();
        }
        if (hit.getMetadata() != null) {
            Object value = hit.getMetadata().get("documentTitle");
            if (value == null) {
                value = hit.getMetadata().get("title");
            }
            if (value == null) {
                value = hit.getMetadata().get("filename");
            }
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return UNKNOWN_DOCUMENT_TITLE;
    }

    private String normalizeChunkNo(KnowledgeHit hit) {
        return hit.getChunkNo() == null ? UNKNOWN_CHUNK_NO : String.valueOf(hit.getChunkNo());
    }

    private String normalizeScore(KnowledgeHit hit) {
        return hit.getScore() == null ? "未知" : String.format(Locale.ROOT, "%.4f", hit.getScore());
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (text.length() + TOKEN_CHAR_RATIO - 1) / TOKEN_CHAR_RATIO);
    }

    private int resolveMaxContextTokens(AiProperties aiProperties) {
        if (aiProperties == null || aiProperties.getRag() == null
                || aiProperties.getRag().getMaxContextTokens() == null
                || aiProperties.getRag().getMaxContextTokens() <= 0) {
            return DEFAULT_MAX_CONTEXT_TOKENS;
        }
        return aiProperties.getRag().getMaxContextTokens();
    }

    private record ContextBuildResult(String context, List<KnowledgeHit> knowledgeHits, boolean truncated,
                                      int maxContextChars) {
    }

}
