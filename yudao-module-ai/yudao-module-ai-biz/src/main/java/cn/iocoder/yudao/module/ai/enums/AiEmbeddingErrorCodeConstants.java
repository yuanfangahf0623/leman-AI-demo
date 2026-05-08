package cn.iocoder.yudao.module.ai.enums;

/**
 * AI Embedding 错误码。
 */
public interface AiEmbeddingErrorCodeConstants {

    Integer EMBEDDING_PROVIDER_UNSUPPORTED = 1_020_004_001;
    Integer EMBEDDING_OPENAI_COMPATIBLE_NOT_IMPLEMENTED = 1_020_004_002;
    Integer EMBEDDING_DIMENSIONS_INVALID = 1_020_004_003;
    Integer EMBEDDING_CONFIG_INVALID = 1_020_004_004;
    Integer EMBEDDING_REQUEST_FAILED = 1_020_004_005;
    Integer EMBEDDING_RESPONSE_INVALID = 1_020_004_006;

}
