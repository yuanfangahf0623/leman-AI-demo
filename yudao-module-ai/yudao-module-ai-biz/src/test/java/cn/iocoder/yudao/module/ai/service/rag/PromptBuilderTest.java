package cn.iocoder.yudao.module.ai.service.rag;

import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    @Test
    void buildShouldReturnPromptWhenHitExists() {
        PromptBuilder builder = new PromptBuilder(new AiProperties());

        PromptBuildResult result = builder.build("报销流程是什么？", List.of(KnowledgeHit.builder()
                .documentTitle("财务制度")
                .chunkNo(3)
                .content("员工报销需要提交发票、审批单，并由直属主管审批。")
                .score(0.91D)
                .build()));

        assertFalse(result.isNoContext());
        assertEquals(PromptBuildResult.STATUS_NORMAL, result.getStatus());
        assertTrue(result.getSystemPrompt().contains("请严格基于给定的知识片段回答问题"));
        assertTrue(result.getUserPrompt().contains("用户问题："));
        assertTrue(result.getUserPrompt().contains("报销流程是什么？"));
        assertTrue(result.getContext().contains("文档标题：财务制度"));
        assertTrue(result.getContext().contains("Chunk 编号：3"));
        assertTrue(result.getContext().contains("员工报销需要提交发票"));
    }

    @Test
    void buildShouldReturnNoContextWhenHitsEmptyOrInvalid() {
        PromptBuilder builder = new PromptBuilder(new AiProperties());

        PromptBuildResult result = builder.build("没有命中怎么办？", List.of(
                KnowledgeHit.builder().documentTitle("空文档").chunkNo(1).content(" ").build()));

        assertTrue(result.isNoContext());
        assertEquals(PromptBuildResult.STATUS_NO_CONTEXT, result.getStatus());
        assertEquals("", result.getContext());
        assertTrue(result.getUserPrompt().contains("no-context"));
        assertTrue(result.getUserPrompt().contains("根据当前知识库资料无法确认"));
    }

    @Test
    void buildShouldTruncateLongContext() {
        AiProperties properties = new AiProperties();
        properties.getRag().setMaxContextTokens(25);
        PromptBuilder builder = new PromptBuilder(properties);
        String longContent = "这是一个很长的制度内容。".repeat(100);

        PromptBuildResult result = builder.build("制度内容是什么？", List.of(KnowledgeHit.builder()
                .chunkNo(1)
                .content(longContent)
                .score(0.88D)
                .metadata(Map.of("title", "长文档"))
                .build()));

        assertFalse(result.isNoContext());
        assertTrue(result.getContext().contains("文档标题：长文档"));
        assertTrue(result.getContext().contains("[内容已因上下文长度限制截断]"));
        assertTrue(result.getEstimatedContextTokens() <= 25);
    }

}
