package cn.iocoder.yudao.module.ai.service.rag.retrieval;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 办公场景中常见问题类型。
 */
@Getter
@AllArgsConstructor
public enum RetrievalQuestionTypeEnum {

    FACT_QA("事实问答"),
    POLICY_PROCEDURE("制度流程"),
    TROUBLESHOOTING("排查处理"),
    FULL_DOCUMENT_SUMMARY("全文总结"),
    DOCUMENT_TRANSLATION("文档翻译"),
    DOCUMENT_REVIEW("文档审查"),
    STRUCTURED_STATISTICS("统计汇总"),
    STRUCTURED_LIST("清单明细"),
    MULTI_TABLE_ANALYSIS("多表关联"),
    COMPARISON_DECISION("对比决策"),
    PERSONAL_SENSITIVE("个人敏感数据"),
    WEB_SUPPLEMENT("联网补充");

    private final String name;

}
