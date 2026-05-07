package cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI knowledge response.
 */
@Data
public class AiKnowledgeRespVO {

    private Long id;
    private String name;
    private String code;
    private String description;
    private Integer status;
    private String vectorStoreType;
    private String embeddingModel;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer topK;
    private Integer documentCount;
    private Integer chunkCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
