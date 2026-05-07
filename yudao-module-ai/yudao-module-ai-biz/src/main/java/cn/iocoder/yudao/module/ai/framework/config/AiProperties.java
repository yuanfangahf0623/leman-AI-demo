package cn.iocoder.yudao.module.ai.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模块配置属性。
 *
 * <p>配置前缀为 {@code ai}。敏感配置项例如 API Key、Base URL 应通过环境变量、配置中心或部署配置注入，
 * 不允许在代码和配置样例中写入真实密钥。</p>
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * 模型配置。
     */
    private ModelProperties model = new ModelProperties();

    /**
     * 向量库配置。
     */
    private VectorStoreProperties vectorStore = new VectorStoreProperties();

    /**
     * RAG 检索配置。
     */
    private RagProperties rag = new RagProperties();

    /**
     * 文档切片配置。
     */
    private DocumentProperties document = new DocumentProperties();

    public ModelProperties getModel() {
        return model;
    }

    public void setModel(ModelProperties model) {
        this.model = model;
    }

    public VectorStoreProperties getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(VectorStoreProperties vectorStore) {
        this.vectorStore = vectorStore;
    }

    public RagProperties getRag() {
        return rag;
    }

    public void setRag(RagProperties rag) {
        this.rag = rag;
    }

    public DocumentProperties getDocument() {
        return document;
    }

    public void setDocument(DocumentProperties document) {
        this.document = document;
    }

    public static class ModelProperties {

        /**
         * 模型供应商标识，例如 openai-compatible。
         */
        private String provider = "openai-compatible";

        /**
         * 模型服务地址。建议通过 ${AI_BASE_URL} 或配置中心注入。
         */
        private String baseUrl;

        /**
         * 模型服务密钥。建议通过 ${AI_API_KEY} 或配置中心注入。
         */
        private String apiKey;

        /**
         * 默认聊天模型名称。
         */
        private String chatModel = "gpt-4o-mini";

        /**
         * 默认 Embedding 模型名称。
         */
        private String embeddingModel = "text-embedding-3-small";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }
    }

    public static class VectorStoreProperties {

        /**
         * 向量库类型。第一阶段使用 pgvector，预留 qdrant。
         */
        private String type = "pgvector";

        /**
         * PostgreSQL + pgvector 配置。
         */
        private PgvectorProperties pgvector = new PgvectorProperties();

        /**
         * Qdrant 配置预留。
         */
        private QdrantProperties qdrant = new QdrantProperties();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public PgvectorProperties getPgvector() {
            return pgvector;
        }

        public void setPgvector(PgvectorProperties pgvector) {
            this.pgvector = pgvector;
        }

        public QdrantProperties getQdrant() {
            return qdrant;
        }

        public void setQdrant(QdrantProperties qdrant) {
            this.qdrant = qdrant;
        }
    }

    public static class PgvectorProperties {

        /**
         * pgvector 存储表名。
         */
        private String tableName = "ai_vector_store";

        /**
         * 向量维度，需与 Embedding 模型输出维度一致。
         */
        private Integer dimensions = 1536;

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public Integer getDimensions() {
            return dimensions;
        }

        public void setDimensions(Integer dimensions) {
            this.dimensions = dimensions;
        }
    }

    public static class QdrantProperties {

        /**
         * Qdrant 服务地址。
         */
        private String host = "localhost";

        /**
         * Qdrant gRPC 端口。
         */
        private Integer port = 6334;

        /**
         * Qdrant 集合名称。
         */
        private String collectionName = "ai_knowledge";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }
    }

    public static class RagProperties {

        /**
         * 默认召回数量。
         */
        private Integer defaultTopK = 5;

        /**
         * 默认相似度阈值。
         */
        private Double defaultScoreThreshold = 0.7D;

        /**
         * 最大上下文 Token 数。
         */
        private Integer maxContextTokens = 6000;

        /**
         * 是否启用重排序。
         */
        private Boolean enableRerank = false;

        /**
         * 是否启用查询改写。
         */
        private Boolean enableQueryRewrite = false;

        public Integer getDefaultTopK() {
            return defaultTopK;
        }

        public void setDefaultTopK(Integer defaultTopK) {
            this.defaultTopK = defaultTopK;
        }

        public Double getDefaultScoreThreshold() {
            return defaultScoreThreshold;
        }

        public void setDefaultScoreThreshold(Double defaultScoreThreshold) {
            this.defaultScoreThreshold = defaultScoreThreshold;
        }

        public Integer getMaxContextTokens() {
            return maxContextTokens;
        }

        public void setMaxContextTokens(Integer maxContextTokens) {
            this.maxContextTokens = maxContextTokens;
        }

        public Boolean getEnableRerank() {
            return enableRerank;
        }

        public void setEnableRerank(Boolean enableRerank) {
            this.enableRerank = enableRerank;
        }

        public Boolean getEnableQueryRewrite() {
            return enableQueryRewrite;
        }

        public void setEnableQueryRewrite(Boolean enableQueryRewrite) {
            this.enableQueryRewrite = enableQueryRewrite;
        }
    }

    public static class DocumentProperties {

        /**
         * 默认切片大小。
         */
        private Integer defaultChunkSize = 800;

        /**
         * 默认切片重叠大小。
         */
        private Integer defaultChunkOverlap = 100;

        /**
         * 最大文件大小，单位 MB。
         */
        private Integer maxFileSizeMb = 50;

        public Integer getDefaultChunkSize() {
            return defaultChunkSize;
        }

        public void setDefaultChunkSize(Integer defaultChunkSize) {
            this.defaultChunkSize = defaultChunkSize;
        }

        public Integer getDefaultChunkOverlap() {
            return defaultChunkOverlap;
        }

        public void setDefaultChunkOverlap(Integer defaultChunkOverlap) {
            this.defaultChunkOverlap = defaultChunkOverlap;
        }

        public Integer getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        public void setMaxFileSizeMb(Integer maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }
    }
}
