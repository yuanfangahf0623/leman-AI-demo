package cn.iocoder.yudao.module.ai.service.rag.retrieval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalPlannerTest {

    private final RetrievalPlanner planner = new RetrievalPlanner();

    @Test
    void planShouldClassifyStructuredStatistics() {
        RetrievalPlan plan = planner.plan("帮我统计工装对应尺寸的数量");

        assertEquals(RetrievalModeEnum.STRUCTURED_QUERY, plan.getMode());
        assertEquals(RetrievalQuestionTypeEnum.STRUCTURED_STATISTICS, plan.getQuestionType());
        assertTrue(plan.isStructuredDataRequired());
        assertTrue(plan.isStrictEvidenceRequired());
        assertTrue(plan.getPreferredFileTypes().contains(RetrievalFileTypeEnum.EXCEL));
    }

    @Test
    void planShouldClassifyFullDocumentTranslation() {
        RetrievalPlan plan = planner.plan("把这份德文合同全文翻译成中文");

        assertEquals(RetrievalModeEnum.FULL_DOCUMENT, plan.getMode());
        assertEquals(RetrievalQuestionTypeEnum.DOCUMENT_TRANSLATION, plan.getQuestionType());
        assertTrue(plan.isFullDocumentRequired());
        assertTrue(plan.getPreferredFileTypes().contains(RetrievalFileTypeEnum.WORD));
        assertTrue(plan.getPreferredFileTypes().contains(RetrievalFileTypeEnum.PDF));
    }

    @Test
    void planShouldClassifyTroubleshootingAsAdjacentChunks() {
        RetrievalPlan plan = planner.plan("二楼北电脑不能联网应该怎么排查");

        assertEquals(RetrievalModeEnum.ADJACENT_CHUNKS, plan.getMode());
        assertEquals(RetrievalQuestionTypeEnum.TROUBLESHOOTING, plan.getQuestionType());
    }

    @Test
    void planShouldKeepFactQuestionLocalChunk() {
        RetrievalPlan plan = planner.plan("请假需要提前多久");

        assertEquals(RetrievalModeEnum.LOCAL_CHUNK, plan.getMode());
        assertEquals(RetrievalQuestionTypeEnum.FACT_QA, plan.getQuestionType());
    }

}
