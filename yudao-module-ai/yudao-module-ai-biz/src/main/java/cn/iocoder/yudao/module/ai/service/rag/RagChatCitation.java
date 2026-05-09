package cn.iocoder.yudao.module.ai.service.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 问答引用来源。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagChatCitation {

    private Long documentId;

    private Long chunkId;

    private Integer chunkNo;

    private String documentTitle;

    private Double score;

    private String quoteText;

}
