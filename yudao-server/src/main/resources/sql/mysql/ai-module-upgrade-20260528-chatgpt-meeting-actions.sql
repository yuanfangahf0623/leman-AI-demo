SET @ai_meeting_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting'
);

SET @column_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting' AND COLUMN_NAME = 'chatgpt_visible'
);
SET @ddl := IF(@ai_meeting_exists > 0 AND @column_exists = 0,
    'ALTER TABLE `ai_meeting` ADD COLUMN `chatgpt_visible` BIT NOT NULL DEFAULT b''0'' COMMENT ''Visible to ChatGPT Actions''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting' AND COLUMN_NAME = 'sensitivity_level'
);
SET @ddl := IF(@ai_meeting_exists > 0 AND @column_exists = 0,
    'ALTER TABLE `ai_meeting` ADD COLUMN `sensitivity_level` VARCHAR(32) NOT NULL DEFAULT ''NORMAL'' COMMENT ''NORMAL / INTERNAL / CONFIDENTIAL / HR / FINANCE''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting' AND COLUMN_NAME = 'project_code'
);
SET @ddl := IF(@ai_meeting_exists > 0 AND @column_exists = 0,
    'ALTER TABLE `ai_meeting` ADD COLUMN `project_code` VARCHAR(128) NULL COMMENT ''Project code''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ai_chatgpt_action_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT 0,
    action_name VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NULL,
    caller_type VARCHAR(32) DEFAULT 'GPT_ACTION',
    caller_identity VARCHAR(256) NULL,
    meeting_id BIGINT NULL,
    knowledge_base_id BIGINT NULL,
    query_text TEXT NULL,
    success BIT NOT NULL DEFAULT b'1',
    error_code VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT 'ChatGPT Actions audit log';

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chatgpt_action_log'
      AND INDEX_NAME = 'idx_ai_chatgpt_action_log_tenant_time'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_ai_chatgpt_action_log_tenant_time ON ai_chatgpt_action_log (tenant_id, create_time)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chatgpt_action_log'
      AND INDEX_NAME = 'idx_ai_chatgpt_action_log_meeting'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_ai_chatgpt_action_log_meeting ON ai_chatgpt_action_log (meeting_id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chatgpt_action_log'
      AND INDEX_NAME = 'idx_ai_chatgpt_action_log_knowledge_base'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_ai_chatgpt_action_log_knowledge_base ON ai_chatgpt_action_log (knowledge_base_id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chatgpt_action_log'
      AND INDEX_NAME = 'idx_ai_chatgpt_action_log_request'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_ai_chatgpt_action_log_request ON ai_chatgpt_action_log (request_id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
