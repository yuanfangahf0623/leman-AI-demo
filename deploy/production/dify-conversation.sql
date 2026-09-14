CREATE TABLE IF NOT EXISTS ai_dify_conversation (
  tenant_id BIGINT NOT NULL,
  knowledge_base_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  conversation_id BIGINT NOT NULL,
  external_id VARCHAR(128) NOT NULL,
  PRIMARY KEY (tenant_id, knowledge_base_id, user_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
