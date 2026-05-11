-- AI knowledge directory and document version upgrade script.

CREATE TABLE IF NOT EXISTS `ai_knowledge_directory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `knowledge_base_id` bigint NOT NULL COMMENT '知识库编号',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父目录编号',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目录名称',
  `sort` int NOT NULL DEFAULT 0 COMMENT '显示排序',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_ai_dir_kb_parent_tenant_deleted` (`knowledge_base_id`, `parent_id`, `tenant_id`, `deleted`) USING BTREE,
  KEY `idx_ai_dir_tenant_kb_name` (`tenant_id`, `knowledge_base_id`, `name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库目录表，用于维护知识库下的文档目录结构';

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_document' AND COLUMN_NAME = 'directory_id'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `ai_document` ADD COLUMN `directory_id` bigint DEFAULT NULL COMMENT ''知识库目录编号'' AFTER `knowledge_base_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_document' AND COLUMN_NAME = 'document_version'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `ai_document` ADD COLUMN `document_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''v1'' COMMENT ''文档版本'' AFTER `data_source_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_document' AND INDEX_NAME = 'idx_ai_doc_directory'
);
SET @ddl := IF(@index_exists = 0,
  'ALTER TABLE `ai_document` ADD KEY `idx_ai_doc_directory` (`directory_id`) USING BTREE',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
