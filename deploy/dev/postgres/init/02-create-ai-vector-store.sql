-- AI 知识库 pgvector 初始化脚本。
--
-- 默认向量维度为 1536，对应 ai.vector-store.pgvector.dimensions 的默认值。
-- 如果配置中的 dimensions 发生变化，需要同步调整 embedding vector(1536) 的维度。

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS ai_vector_store (
    id BIGSERIAL PRIMARY KEY,
    vector_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding vector(1536) NOT NULL,
    create_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_vector_store IS 'AI 知识库向量存储表，用于 PostgreSQL + pgvector 第一阶段向量检索';
COMMENT ON COLUMN ai_vector_store.id IS '编号';
COMMENT ON COLUMN ai_vector_store.vector_id IS '向量编号，业务侧幂等写入标识';
COMMENT ON COLUMN ai_vector_store.tenant_id IS '租户编号，RAG 检索必须过滤';
COMMENT ON COLUMN ai_vector_store.knowledge_base_id IS '知识库编号，RAG 检索必须过滤';
COMMENT ON COLUMN ai_vector_store.document_id IS '文档编号';
COMMENT ON COLUMN ai_vector_store.chunk_id IS '文档切片编号';
COMMENT ON COLUMN ai_vector_store.content IS '切片文本内容';
COMMENT ON COLUMN ai_vector_store.metadata IS '向量元数据';
COMMENT ON COLUMN ai_vector_store.embedding IS 'Embedding 向量，默认 1536 维；需与 ai.vector-store.pgvector.dimensions 保持一致';
COMMENT ON COLUMN ai_vector_store.create_time IS '创建时间';
COMMENT ON COLUMN ai_vector_store.update_time IS '更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_vector_store_vector_id
    ON ai_vector_store (vector_id);

CREATE INDEX IF NOT EXISTS idx_ai_vector_store_tenant_id
    ON ai_vector_store (tenant_id);

CREATE INDEX IF NOT EXISTS idx_ai_vector_store_knowledge_base_id
    ON ai_vector_store (knowledge_base_id);

CREATE INDEX IF NOT EXISTS idx_ai_vector_store_document_id
    ON ai_vector_store (document_id);

CREATE INDEX IF NOT EXISTS idx_ai_vector_store_chunk_id
    ON ai_vector_store (chunk_id);

CREATE INDEX IF NOT EXISTS idx_ai_vector_store_tenant_kb
    ON ai_vector_store (tenant_id, knowledge_base_id);

DO $$
BEGIN
    BEGIN
        CREATE INDEX IF NOT EXISTS idx_ai_vector_store_embedding_hnsw
            ON ai_vector_store USING hnsw (embedding vector_cosine_ops);
        RAISE NOTICE 'Created HNSW vector index idx_ai_vector_store_embedding_hnsw';
    EXCEPTION
        WHEN undefined_object OR invalid_parameter_value OR feature_not_supported THEN
            RAISE NOTICE 'HNSW vector index is unavailable, fallback to IVFFLAT: %', SQLERRM;
            BEGIN
                CREATE INDEX IF NOT EXISTS idx_ai_vector_store_embedding_ivfflat
                    ON ai_vector_store USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
                RAISE NOTICE 'Created IVFFLAT vector index idx_ai_vector_store_embedding_ivfflat';
            EXCEPTION
                WHEN undefined_object OR invalid_parameter_value OR feature_not_supported THEN
                    RAISE NOTICE 'IVFFLAT vector index is unavailable, vector search will use normal query scan: %', SQLERRM;
            END;
    END;
END
$$;
