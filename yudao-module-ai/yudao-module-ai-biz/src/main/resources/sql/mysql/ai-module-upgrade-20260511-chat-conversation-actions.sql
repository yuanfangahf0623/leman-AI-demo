-- AI 问答会话操作字段升级脚本：支持置顶、归档、重命名和删除。

SET @column_exists = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_conversation' AND COLUMN_NAME = 'pinned'
);
SET @sql = IF(@column_exists = 0,
  'ALTER TABLE `ai_chat_conversation` ADD COLUMN `pinned` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否置顶'' AFTER `status`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_conversation' AND COLUMN_NAME = 'pinned_time'
);
SET @sql = IF(@column_exists = 0,
  'ALTER TABLE `ai_chat_conversation` ADD COLUMN `pinned_time` datetime DEFAULT NULL COMMENT ''置顶时间'' AFTER `pinned`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_conversation' AND INDEX_NAME = 'idx_ai_conversation_pinned'
);
SET @sql = IF(@index_exists = 0,
  'CREATE INDEX `idx_ai_conversation_pinned` ON `ai_chat_conversation` (`tenant_id`, `user_id`, `pinned`, `pinned_time`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
