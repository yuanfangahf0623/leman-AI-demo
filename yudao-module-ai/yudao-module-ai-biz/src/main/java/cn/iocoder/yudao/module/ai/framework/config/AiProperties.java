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

        /**
         * 外部模型服务连接超时时间，单位秒。
         */
        private Integer connectTimeoutSeconds = 10;

        /**
         * 外部模型服务请求超时时间，单位秒。
         */
        private Integer readTimeoutSeconds = 60;

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

        public Integer getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(Integer connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public Integer getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }

        public void setReadTimeoutSeconds(Integer readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
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
         * pgvector PostgreSQL JDBC 地址。为空时兼容使用 Spring 主 JdbcTemplate。
         */
        private String jdbcUrl;

        /**
         * pgvector PostgreSQL 用户名。
         */
        private String username;

        /**
         * pgvector PostgreSQL 密码。必须通过环境变量、配置中心或部署配置注入。
         */
        private String password;

        /**
         * pgvector 存储表名。
         */
        private String tableName = "ai_vector_store";

        /**
         * 向量维度，需与 Embedding 模型输出维度一致。
         */
        private Integer dimensions = 1536;

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

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
        private Double defaultScoreThreshold = 0.1D;

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

        /**
         * 文档向量化批量大小，避免一次处理过多 chunk。
         */
        private Integer embeddingBatchSize = 32;

        /**
         * 文档上传本地存储目录。生产环境应切换为对象存储实现，例如 MinIO。
         */
        private String storageBasePath = ".data/ai-documents";

        /**
         * OCR 识别配置。默认关闭，避免本地未安装 OCR 引擎时影响普通文档解析。
         */
        private OcrProperties ocr = new OcrProperties();

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

        public Integer getEmbeddingBatchSize() {
            return embeddingBatchSize;
        }

        public void setEmbeddingBatchSize(Integer embeddingBatchSize) {
            this.embeddingBatchSize = embeddingBatchSize;
        }

        public String getStorageBasePath() {
            return storageBasePath;
        }

        public void setStorageBasePath(String storageBasePath) {
            this.storageBasePath = storageBasePath;
        }

        public OcrProperties getOcr() {
            return ocr;
        }

        public void setOcr(OcrProperties ocr) {
            this.ocr = ocr;
        }
    }

    public static class OcrProperties {

        /**
         * 是否启用 OCR。扫描件或图片型 PDF 需要开启后才能提取文本。
         */
        private Boolean enabled = false;

        /**
         * OCR 提供方。第一阶段仅支持本地 tesseract-cli，后续可扩展云 OCR。
         */
        private String provider = "tesseract-cli";

        /**
         * Tesseract 可执行文件路径。生产环境建议通过环境变量或部署配置指定。
         */
        private String tesseractExecutable = "tesseract";

        /**
         * Tesseract 语言包目录。为空时使用 Tesseract 默认 tessdata 目录。
         */
        private String tessdataDirectory;

        /**
         * OCR 识别语言。中文和英文混排场景使用 chi_sim+eng。
         */
        private String language = "chi_sim+eng";

        /**
         * PDF 渲染图片 DPI。数值越高识别率越好，但 CPU 和内存消耗也越高。
         */
        private Integer dpi = 200;

        /**
         * 单个 PDF 最多 OCR 页数，避免超大文件长时间占用资源。
         */
        private Integer maxPages = 20;

        /**
         * 单页 OCR 超时时间，单位秒。
         */
        private Integer timeoutSeconds = 60;

        /**
         * PDF 原生文本长度达到该阈值时跳过 OCR。
         */
        private Integer minTextLengthToSkipOcr = 20;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getTesseractExecutable() {
            return tesseractExecutable;
        }

        public void setTesseractExecutable(String tesseractExecutable) {
            this.tesseractExecutable = tesseractExecutable;
        }

        public String getTessdataDirectory() {
            return tessdataDirectory;
        }

        public void setTessdataDirectory(String tessdataDirectory) {
            this.tessdataDirectory = tessdataDirectory;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public Integer getDpi() {
            return dpi;
        }

        public void setDpi(Integer dpi) {
            this.dpi = dpi;
        }

        public Integer getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(Integer maxPages) {
            this.maxPages = maxPages;
        }

        public Integer getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public Integer getMinTextLengthToSkipOcr() {
            return minTextLengthToSkipOcr;
        }

        public void setMinTextLengthToSkipOcr(Integer minTextLengthToSkipOcr) {
            this.minTextLengthToSkipOcr = minTextLengthToSkipOcr;
        }
    }
}
