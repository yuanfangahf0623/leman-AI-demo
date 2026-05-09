package cn.iocoder.yudao.module.ai.service.rag;

import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RAG 问答 Prompt 构建器。
 *
 * <p>执行步骤说明：
 * 1. 过滤空命中和空内容，只选择可用于回答的 KnowledgeHit；
 * 2. 按向量检索或重排后的输入顺序拼接 context，不在 PromptBuilder 内重新排序；
 * 3. 每个 context 片段包含文档标题、chunk 编号、相似度和内容；
 * 4. 使用 ai.rag.max-context-tokens 控制最大上下文长度，当前阶段按 1 token 约 4 字符估算；
 * 5. 无有效 context 时返回 no-context 标识，并要求模型输出固定兜底语。</p>
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
        List<KnowledgeHit> effectiveHits = selectEffectiveHits(knowledgeHits);
        if (effectiveHits.isEmpty()) {
            return buildNoContextPrompt(safeQuestion);
        }

        ContextBuildResult contextBuildResult = buildContext(effectiveHits);
        if (contextBuildResult.context().isBlank()) {
            return buildNoContextPrompt(safeQuestion);
        }
        return PromptBuildResult.builder()
                .status(PromptBuildResult.STATUS_NORMAL)
                .systemPrompt(SYSTEM_PROMPT)
                .userPrompt(buildUserPrompt(safeQuestion, contextBuildResult.context()))
                .context(contextBuildResult.context())
                .estimatedContextTokens(estimateTokens(contextBuildResult.context()))
                .knowledgeHits(contextBuildResult.knowledgeHits())
                .build();
    }

    /**
     * 只过滤无效命中，不在这里做 score 阈值判断；阈值应由向量检索层或 RAG 编排层负责。
     */
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

    /**
     * 按命中顺序拼接 context，并在达到最大上下文长度后停止追加后续片段。
     */
    private ContextBuildResult buildContext(List<KnowledgeHit> effectiveHits) {
        StringBuilder context = new StringBuilder();
        List<KnowledgeHit> usedHits = new ArrayList<>();
        int maxContextChars = maxContextTokens * TOKEN_CHAR_RATIO;
        for (int i = 0; i < effectiveHits.size(); i++) {
            if (context.length() >= maxContextChars) {
                break;
            }
            String fragment = buildContextFragment(i + 1, effectiveHits.get(i));
            appendWithLimit(context, fragment, maxContextChars);
            usedHits.add(effectiveHits.get(i));
        }
        return new ContextBuildResult(context.toString().trim(), usedHits);
    }

    /**
     * 单个片段包含来源序号、文档标题、chunk 编号和内容，便于模型列出来源。
     */
    private String buildContextFragment(int index, KnowledgeHit hit) {
        return """
                
                【来源%s】
                文档标题：%s
                Chunk 编号：%s
                相似度：%s
                内容：
                %s
                """.formatted(index, normalizeDocumentTitle(hit), normalizeChunkNo(hit), normalizeScore(hit), hit.getContent().trim());
    }

    /**
     * 超长 context 按字符预算截断；预算来自 max-context-tokens 的估算值。
     */
    private void appendWithLimit(StringBuilder context, String fragment, int maxContextChars) {
        int remainingChars = maxContextChars - context.length();
        if (remainingChars <= 0) {
            return;
        }
        if (fragment.length() <= remainingChars) {
            context.append(fragment);
            return;
        }
        int markerLength = Math.min(TRUNCATED_MARKER.length(), remainingChars);
        int contentLength = Math.max(0, remainingChars - markerLength);
        context.append(fragment, 0, contentLength);
        context.append(TRUNCATED_MARKER, 0, markerLength);
    }

    private PromptBuildResult buildNoContextPrompt(String question) {
        String userPrompt = """
                用户问题：
                %s
                知识片段：
                no-context
                回答要求：
                请回答“根据当前知识库资料无法确认”。
                """.formatted(question);
        return PromptBuildResult.builder()
                .status(PromptBuildResult.STATUS_NO_CONTEXT)
                .systemPrompt(SYSTEM_PROMPT)
                .userPrompt(userPrompt)
                .context("")
                .estimatedContextTokens(0)
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

    private record ContextBuildResult(String context, List<KnowledgeHit> knowledgeHits) {
    }

}
