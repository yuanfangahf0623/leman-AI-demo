package cn.iocoder.yudao.module.ai.service.rag;

/**
 * RAG 问答服务。
 */
public interface RagService {

    RagChatResponse chat(RagChatRequest request);

}
