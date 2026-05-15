package cn.iocoder.yudao.module.ai.service.rag.retrieval;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * RAG 检索计划器。
 *
 * <p>先判断问题需要的证据范围，再由编排层执行对应检索策略。</p>
 */
@Component
public class RetrievalPlanner {

    private static final List<String> STRUCTURED_QUERY_KEYWORDS = List.of("统计", "汇总", "合计", "总数", "数量",
            "多少", "几条", "几项", "几个", "占比", "比例", "平均", "最大", "最小", "明细", "清单", "对应",
            "关联", "多表", "group by", "count", "total", "sum", "average", "avg", "max", "min");
    private static final List<String> MULTI_TABLE_KEYWORDS = List.of("关联", "多表", "同时", "结合", "合并",
            "工资", "绩效", "考勤", "全勤", "人员", "部门", "岗位");
    private static final List<String> LIST_KEYWORDS = List.of("清单", "明细", "列表", "有哪些", "哪几项", "全部");

    private static final List<String> FULL_DOCUMENT_KEYWORDS = List.of("全文", "整篇", "整个文档", "完整文档",
            "总结这份", "总结全文", "全文总结", "翻译全文", "整篇翻译", "全文翻译", "审查全文", "全文审查",
            "通读", "全部内容");
    private static final List<String> TRANSLATION_KEYWORDS = List.of("翻译", "译成中文", "译成英文", "德文",
            "英文", "日文", "韩文");
    private static final List<String> REVIEW_KEYWORDS = List.of("审查", "风险", "条款", "合同", "法务",
            "漏洞", "异常");

    private static final List<String> SECTION_KEYWORDS = List.of("章节", "这一章", "本章", "小节", "段落",
            "目录", "第几章", "第几节");

    private static final List<String> ADJACENT_CONTEXT_KEYWORDS = List.of("流程", "步骤", "规定", "制度",
            "如何", "怎么", "排查", "原因", "依据", "前提", "条件");

    public RetrievalPlan plan(String question) {
        String source = question == null ? "" : question.trim();
        String normalized = source.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, FULL_DOCUMENT_KEYWORDS)) {
            return RetrievalPlan.builder()
                    .mode(RetrievalModeEnum.FULL_DOCUMENT)
                    .questionType(resolveFullDocumentQuestionType(normalized))
                    .preferredFileTypes(allFileTypes())
                    .fullDocumentRequired(true)
                    .strictEvidenceRequired(true)
                    .cacheable(false)
                    .reason("命中全文型任务，需要读取命中文档的完整内容")
                    .executionNotes(List.of("Word/PDF/TXT/MD 适合全文总结、翻译、审查；Excel/PPT 需要按 Sheet/页逐段处理"))
                    .build();
        }
        if (containsAny(normalized, STRUCTURED_QUERY_KEYWORDS)) {
            return RetrievalPlan.builder()
                    .mode(RetrievalModeEnum.STRUCTURED_QUERY)
                    .questionType(resolveStructuredQuestionType(normalized))
                    .preferredFileTypes(List.of(RetrievalFileTypeEnum.EXCEL, RetrievalFileTypeEnum.WORD,
                            RetrievalFileTypeEnum.PDF, RetrievalFileTypeEnum.TXT, RetrievalFileTypeEnum.MARKDOWN))
                    .structuredDataRequired(true)
                    .strictEvidenceRequired(true)
                    .cacheable(false)
                    .reason("命中统计/汇总/清单/关联类任务，需要完整数据集")
                    .executionNotes(List.of("统计类问题不能只依赖 topK chunk", "Excel 优先进入结构化查询；Word/PDF 表格需先表格抽取",
                            "多表关联需要受控查询计划，不能让模型自由拼接"))
                    .build();
        }
        if (containsAny(normalized, SECTION_KEYWORDS)) {
            return RetrievalPlan.builder()
                    .mode(RetrievalModeEnum.SECTION_EXPANSION)
                    .questionType(RetrievalQuestionTypeEnum.FACT_QA)
                    .preferredFileTypes(List.of(RetrievalFileTypeEnum.WORD, RetrievalFileTypeEnum.PDF,
                            RetrievalFileTypeEnum.MARKDOWN, RetrievalFileTypeEnum.TXT))
                    .cacheable(true)
                    .reason("命中章节型问题，优先保留章节扩展能力")
                    .executionNotes(List.of("按标题、目录、页码或 Markdown heading 定位章节后扩展上下文"))
                    .build();
        }
        if (containsAny(normalized, ADJACENT_CONTEXT_KEYWORDS)) {
            return RetrievalPlan.builder()
                    .mode(RetrievalModeEnum.ADJACENT_CHUNKS)
                    .questionType(resolveAdjacentQuestionType(normalized))
                    .preferredFileTypes(allFileTypes())
                    .cacheable(true)
                    .reason("命中流程/条款/排查类问题，允许补充命中片段前后文")
                    .executionNotes(List.of("命中片段前后文用于补齐步骤、条件、例外说明"))
                    .build();
        }
        return RetrievalPlan.localChunk();
    }

    private RetrievalQuestionTypeEnum resolveStructuredQuestionType(String normalized) {
        if (containsAny(normalized, MULTI_TABLE_KEYWORDS)) {
            return RetrievalQuestionTypeEnum.MULTI_TABLE_ANALYSIS;
        }
        if (containsAny(normalized, LIST_KEYWORDS)) {
            return RetrievalQuestionTypeEnum.STRUCTURED_LIST;
        }
        return RetrievalQuestionTypeEnum.STRUCTURED_STATISTICS;
    }

    private RetrievalQuestionTypeEnum resolveFullDocumentQuestionType(String normalized) {
        if (containsAny(normalized, TRANSLATION_KEYWORDS)) {
            return RetrievalQuestionTypeEnum.DOCUMENT_TRANSLATION;
        }
        if (containsAny(normalized, REVIEW_KEYWORDS)) {
            return RetrievalQuestionTypeEnum.DOCUMENT_REVIEW;
        }
        return RetrievalQuestionTypeEnum.FULL_DOCUMENT_SUMMARY;
    }

    private RetrievalQuestionTypeEnum resolveAdjacentQuestionType(String normalized) {
        if (containsAny(normalized, List.of("排查", "故障", "不能", "异常", "处理"))) {
            return RetrievalQuestionTypeEnum.TROUBLESHOOTING;
        }
        return RetrievalQuestionTypeEnum.POLICY_PROCEDURE;
    }

    private List<RetrievalFileTypeEnum> allFileTypes() {
        return List.of(RetrievalFileTypeEnum.TXT, RetrievalFileTypeEnum.MARKDOWN, RetrievalFileTypeEnum.PDF,
                RetrievalFileTypeEnum.WORD, RetrievalFileTypeEnum.EXCEL, RetrievalFileTypeEnum.POWERPOINT);
    }

    private boolean containsAny(String source, List<String> keywords) {
        if (source == null || source.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank()
                    && source.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

}
