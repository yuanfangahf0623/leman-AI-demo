package cn.iocoder.yudao.module.ai.enums;

/**
 * AI RAG 错误码。
 */
public interface AiRagErrorCodeConstants {

    Integer RAG_REQUEST_INVALID = 1_020_007_001;
    Integer RAG_KNOWLEDGE_NOT_EXISTS = 1_020_007_002;
    Integer RAG_KNOWLEDGE_ACCESS_DENIED = 1_020_007_003;
    Integer RAG_CONVERSATION_NOT_EXISTS = 1_020_007_004;
    Integer RAG_CONVERSATION_ACCESS_DENIED = 1_020_007_005;
    Integer RAG_STREAM_NOT_SUPPORTED = 1_020_007_006;
    Integer RAG_PERSONAL_SENSITIVE_ACCESS_DENIED = 1_020_007_007;

}
