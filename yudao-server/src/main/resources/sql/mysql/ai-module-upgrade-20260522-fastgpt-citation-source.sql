-- FastGPT 引用来源补充外部知识库名称，用于历史会话列表展示命中的 FastGPT 数据集/知识库。
SET @column_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_citation' AND COLUMN_NAME = 'external_knowledge_base_name'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `ai_chat_citation` ADD COLUMN `external_knowledge_base_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''外部知识库名称，例如 FastGPT 数据集名称'' AFTER `knowledge_base_id`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
