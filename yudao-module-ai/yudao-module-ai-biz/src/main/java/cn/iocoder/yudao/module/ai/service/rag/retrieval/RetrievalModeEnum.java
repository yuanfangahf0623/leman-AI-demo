package cn.iocoder.yudao.module.ai.service.rag.retrieval;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * RAG 检索计划模式。
 *
 * <p>不同问题需要不同证据范围，不能全部按 topK chunk 处理。</p>
 */
@Getter
@AllArgsConstructor
public enum RetrievalModeEnum {

    LOCAL_CHUNK("局部片段", "普通事实问答，只使用命中的 chunk"),
    ADJACENT_CHUNKS("相邻片段", "流程、条款解释类问题，允许补充命中 chunk 前后文"),
    SECTION_EXPANSION("章节扩展", "章节级问题，后续按标题和章节边界扩展"),
    FULL_DOCUMENT("全文扩展", "全文总结、全文翻译、全文审查等任务"),
    STRUCTURED_QUERY("结构化查询", "统计、汇总、清单、多表关联等需要完整数据集的问题");

    private final String name;
    private final String description;

}
