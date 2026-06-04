CREATE TABLE IF NOT EXISTS ai_meeting (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT 0,
    subject VARCHAR(255) NOT NULL,
    summary TEXT NULL,
    organizer_name VARCHAR(255) NULL,
    start_time DATETIME NULL,
    end_time DATETIME NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'TEAMS',
    source_meeting_id VARCHAR(256) NULL,
    source_online_meeting_id VARCHAR(512) NULL,
    knowledge_base_id BIGINT NULL,
    transcript_document_id BIGINT NULL,
    minutes_document_id BIGINT NULL,
    minutes_status VARCHAR(32) NULL,
    transcript_status VARCHAR(32) NULL,
    sync_status VARCHAR(32) NULL,
    error_message VARCHAR(1024) NULL,
    chatgpt_visible BIT NOT NULL DEFAULT b'0',
    sensitivity_level VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    project_code VARCHAR(128) NULL,
    deleted BIT NOT NULL DEFAULT b'0',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT 'AI meeting';

CREATE TABLE IF NOT EXISTS ai_meeting_transcript (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT 0,
    meeting_id BIGINT NOT NULL,
    source_transcript_id VARCHAR(256) NULL,
    cleaned_content MEDIUMTEXT NULL,
    content MEDIUMTEXT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT 'AI meeting transcript';

CREATE TABLE IF NOT EXISTS ai_meeting_minutes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT 0,
    meeting_id BIGINT NOT NULL,
    title VARCHAR(255) NULL,
    summary TEXT NULL,
    key_points_json JSON NULL,
    decisions_json JSON NULL,
    action_items_json JSON NULL,
    risks_json JSON NULL,
    open_questions_json JSON NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT 'AI meeting minutes';

CREATE TABLE IF NOT EXISTS ai_meeting_action_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT 0,
    meeting_id BIGINT NOT NULL,
    task TEXT NULL,
    owner VARCHAR(255) NULL,
    deadline DATE NULL,
    deadline_text VARCHAR(128) NULL,
    priority VARCHAR(32) NULL,
    source_quote TEXT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT 'AI meeting action item';

SET @column_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting' AND COLUMN_NAME = 'source_meeting_id'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE `ai_meeting` ADD COLUMN `source_meeting_id` VARCHAR(256) NULL COMMENT ''Teams event id''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting' AND COLUMN_NAME = 'source_online_meeting_id'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE `ai_meeting` ADD COLUMN `source_online_meeting_id` VARCHAR(512) NULL COMMENT ''Teams online meeting id''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting' AND COLUMN_NAME = 'transcript_document_id'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE `ai_meeting` ADD COLUMN `transcript_document_id` BIGINT NULL COMMENT ''Transcript knowledge document id''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting' AND COLUMN_NAME = 'minutes_document_id'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE `ai_meeting` ADD COLUMN `minutes_document_id` BIGINT NULL COMMENT ''Minutes knowledge document id''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting' AND COLUMN_NAME = 'sync_status'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE `ai_meeting` ADD COLUMN `sync_status` VARCHAR(32) NULL COMMENT ''Teams sync status''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting' AND COLUMN_NAME = 'error_message'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE `ai_meeting` ADD COLUMN `error_message` VARCHAR(1024) NULL COMMENT ''Teams sync error message''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting_transcript' AND COLUMN_NAME = 'source_transcript_id'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE `ai_meeting_transcript` ADD COLUMN `source_transcript_id` VARCHAR(256) NULL COMMENT ''Teams transcript id''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting'
      AND INDEX_NAME = 'uk_ai_meeting_source'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_ai_meeting_source ON ai_meeting (tenant_id, source_type, source_meeting_id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting'
      AND INDEX_NAME = 'idx_ai_meeting_chatgpt_filter'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_ai_meeting_chatgpt_filter ON ai_meeting (tenant_id, chatgpt_visible, sensitivity_level, start_time)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting_transcript'
      AND INDEX_NAME = 'uk_ai_meeting_transcript_meeting'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_ai_meeting_transcript_meeting ON ai_meeting_transcript (tenant_id, meeting_id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting_minutes'
      AND INDEX_NAME = 'uk_ai_meeting_minutes_meeting'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_ai_meeting_minutes_meeting ON ai_meeting_minutes (tenant_id, meeting_id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_meeting_action_item'
      AND INDEX_NAME = 'idx_ai_meeting_action_item_meeting'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_ai_meeting_action_item_meeting ON ai_meeting_action_item (tenant_id, meeting_id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
