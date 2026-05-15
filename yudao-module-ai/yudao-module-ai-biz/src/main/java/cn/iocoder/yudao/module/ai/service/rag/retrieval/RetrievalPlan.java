package cn.iocoder.yudao.module.ai.service.rag.retrieval;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * RAG 检索计划。
 *
 * <p>计划只描述证据获取策略，不直接访问数据库和外部模型。</p>
 */
@Data
@Builder
public class RetrievalPlan {

    private RetrievalModeEnum mode;

    private RetrievalQuestionTypeEnum questionType;

    private List<RetrievalFileTypeEnum> preferredFileTypes;

    /**
     * 是否需要完整文档证据，例如全文总结、全文翻译。
     */
    private boolean fullDocumentRequired;

    /**
     * 是否需要结构化完整数据集，例如统计、汇总、清单、多表关联。
     */
    private boolean structuredDataRequired;

    /**
     * 是否需要更严格的证据完整性。命中该标记的问题不应使用旧的问答缓存直接返回。
     */
    private boolean strictEvidenceRequired;

    /**
     * 是否允许进入问题缓存。统计和全文任务默认不缓存，避免源数据变化后继续复用旧结果。
     */
    private boolean cacheable;

    /**
     * 计划生成原因，用于日志和 Prompt 调试信息。
     */
    private String reason;

    private List<String> executionNotes;

    public static RetrievalPlan localChunk() {
        return RetrievalPlan.builder()
                .mode(RetrievalModeEnum.LOCAL_CHUNK)
                .questionType(RetrievalQuestionTypeEnum.FACT_QA)
                .preferredFileTypes(List.of(RetrievalFileTypeEnum.TXT, RetrievalFileTypeEnum.MARKDOWN,
                        RetrievalFileTypeEnum.PDF, RetrievalFileTypeEnum.WORD, RetrievalFileTypeEnum.EXCEL,
                        RetrievalFileTypeEnum.POWERPOINT))
                .cacheable(true)
                .reason("普通问答，使用命中的局部 chunk")
                .executionNotes(List.of("适合制度条款、定义、单点事实；不保证覆盖完整清单或统计总量"))
                .build();
    }

}
