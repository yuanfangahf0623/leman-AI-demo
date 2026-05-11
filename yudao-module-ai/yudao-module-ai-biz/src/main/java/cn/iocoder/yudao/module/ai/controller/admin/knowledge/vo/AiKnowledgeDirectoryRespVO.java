package cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 知识库目录响应。
 */
@Data
public class AiKnowledgeDirectoryRespVO {

    private Long id;
    private Long knowledgeBaseId;
    private Long parentId;
    private String name;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
