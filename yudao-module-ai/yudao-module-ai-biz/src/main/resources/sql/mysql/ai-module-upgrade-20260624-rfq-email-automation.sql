CREATE TABLE IF NOT EXISTS `rfq` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    `customer` VARCHAR(255) NULL COMMENT 'Customer name',
    `product` VARCHAR(512) NULL COMMENT 'Primary product',
    `products_json` JSON NULL COMMENT 'Hermes products array',
    `status` VARCHAR(32) NOT NULL DEFAULT 'NEW' COMMENT 'NEW / ANALYZING / COSTING / QUOTED / SENT',
    `risk_score` DECIMAL(10, 2) NULL COMMENT 'Hermes risk score',
    `confidence` DECIMAL(5, 4) NULL COMMENT 'Hermes confidence, 0-1',
    `message_id` VARCHAR(512) NOT NULL COMMENT 'Email Message-ID or stable account UID fallback',
    `source_account` VARCHAR(255) NULL COMMENT 'Mailbox account key',
    `email_from` VARCHAR(512) NULL COMMENT 'Email sender',
    `email_subject` VARCHAR(512) NULL COMMENT 'Email subject',
    `received_time` DATETIME NULL COMMENT 'Email received time',
    `missing_info_json` JSON NULL COMMENT 'Hermes missing info array',
    `next_actions_json` JSON NULL COMMENT 'Hermes next actions array',
    `raw_result_json` JSON NULL COMMENT 'Hermes strict JSON output',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT b'0'
) COMMENT 'RFQ';

CREATE TABLE IF NOT EXISTS `rfq_task` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    `rfq_id` BIGINT NOT NULL COMMENT 'RFQ id',
    `task_type` VARCHAR(32) NOT NULL COMMENT 'ENGINEERING / COSTING / PROCUREMENT / SALES',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PROCESSING / DONE / CANCELLED',
    `owner` VARCHAR(128) NULL COMMENT 'Owner role or user',
    `title` VARCHAR(255) NULL COMMENT 'Task title',
    `detail` TEXT NULL COMMENT 'Task detail',
    `due_date` DATE NULL COMMENT 'Due date',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT b'0'
) COMMENT 'RFQ task';

CREATE TABLE IF NOT EXISTS `email_sync_state` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    `account` VARCHAR(255) NOT NULL COMMENT 'Mailbox account key',
    `last_uid` BIGINT NOT NULL DEFAULT 0 COMMENT 'Last processed IMAP UID',
    `last_sync_time` DATETIME NULL COMMENT 'Last sync time',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT b'0'
) COMMENT 'Email sync state';

CREATE TABLE IF NOT EXISTS `rfq_mailbox_account` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    `account` VARCHAR(255) NOT NULL COMMENT 'Stable mailbox account key',
    `email_address` VARCHAR(255) NOT NULL COMMENT 'Mailbox email address',
    `host` VARCHAR(255) NOT NULL COMMENT 'IMAP host',
    `port` INT NOT NULL DEFAULT 993 COMMENT 'IMAP SSL port',
    `username` VARCHAR(255) NOT NULL COMMENT 'IMAP username',
    `password_ciphertext` VARCHAR(2048) NOT NULL COMMENT 'Encrypted IMAP authorization password',
    `password_mask` VARCHAR(128) NOT NULL COMMENT 'Masked password for display',
    `folder` VARCHAR(128) NOT NULL DEFAULT 'INBOX' COMMENT 'IMAP folder',
    `enabled` BIT NOT NULL DEFAULT b'1' COMMENT 'Whether this mailbox is enabled',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT b'0'
) COMMENT 'RFQ mailbox account';

CREATE TABLE IF NOT EXISTS `email_attachment` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    `account` VARCHAR(255) NULL COMMENT 'Mailbox account key',
    `message_id` VARCHAR(512) NOT NULL COMMENT 'Email Message-ID or stable account UID fallback',
    `message_hash` VARCHAR(64) NOT NULL COMMENT 'SHA-256 hash of message_id',
    `uid` BIGINT NULL COMMENT 'IMAP UID',
    `rfq_id` BIGINT NULL COMMENT 'Linked RFQ id when the email is classified as RFQ',
    `file_name` VARCHAR(512) NOT NULL COMMENT 'Original attachment file name for display',
    `content_type` VARCHAR(255) NULL COMMENT 'Attachment content type',
    `file_size` BIGINT NULL COMMENT 'Attachment size in bytes',
    `object_key` VARCHAR(1024) NOT NULL COMMENT 'Object storage key for the raw attachment file',
    `source_uri` VARCHAR(1024) NULL COMMENT 'Object storage URI for the raw attachment file',
    `content_hash` VARCHAR(64) NOT NULL COMMENT 'SHA-256 hash of raw attachment content',
    `extracted_text` MEDIUMTEXT NULL COMMENT 'Clean extracted attachment text, raw binary is not stored in DB',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT b'0'
) COMMENT 'Email attachment';

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rfq'
      AND INDEX_NAME = 'uk_rfq_message_id'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_rfq_message_id ON rfq (message_id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_attachment'
      AND INDEX_NAME = 'uk_email_attachment_msg_file_hash'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_email_attachment_msg_file_hash ON email_attachment (tenant_id, message_hash, content_hash, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_attachment'
      AND INDEX_NAME = 'idx_email_attachment_rfq'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_email_attachment_rfq ON email_attachment (tenant_id, rfq_id, id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_attachment'
      AND INDEX_NAME = 'idx_email_attachment_message'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_email_attachment_message ON email_attachment (tenant_id, message_hash, id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rfq_mailbox_account'
      AND INDEX_NAME = 'uk_rfq_mailbox_account'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_rfq_mailbox_account ON rfq_mailbox_account (tenant_id, account)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rfq_mailbox_account'
      AND INDEX_NAME = 'idx_rfq_mailbox_enabled'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_rfq_mailbox_enabled ON rfq_mailbox_account (tenant_id, enabled)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rfq'
      AND INDEX_NAME = 'idx_rfq_tenant_status'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_rfq_tenant_status ON rfq (tenant_id, status, create_time)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rfq_task'
      AND INDEX_NAME = 'idx_rfq_task_rfq'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_rfq_task_rfq ON rfq_task (tenant_id, rfq_id, task_type)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_sync_state'
      AND INDEX_NAME = 'uk_email_sync_state_account'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_email_sync_state_account ON email_sync_state (tenant_id, account)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
