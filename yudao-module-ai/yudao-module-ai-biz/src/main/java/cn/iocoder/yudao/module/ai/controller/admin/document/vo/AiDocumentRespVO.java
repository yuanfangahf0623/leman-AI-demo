package cn.iocoder.yudao.module.ai.controller.admin.document.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 文档响应。
 */
@Data
public class AiDocumentRespVO {

    private Long id;
    private Long knowledgeBaseId;
    private Long directoryId;
    private Long dataSourceId;
    private String documentVersion;
    private String title;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String objectKey;
    private String sourceUri;
    private String contentHash;
    private Integer parseStatus;
    private Integer embeddingStatus;
    private Integer chunkCount;
    private Integer tokenCount;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
