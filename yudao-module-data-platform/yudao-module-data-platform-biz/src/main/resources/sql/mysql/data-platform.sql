-- 单租户数据中台核心表。凭据只保存 AES-GCM 密文，部署密钥来自环境变量。

CREATE TABLE IF NOT EXISTS `dp_data_source` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据源编号',
  `name` varchar(100) NOT NULL COMMENT '数据源名称',
  `code` varchar(64) NOT NULL COMMENT '数据源唯一编码',
  `type` varchar(32) NOT NULL COMMENT '类型：MYSQL/POSTGRESQL/SQLSERVER/DORIS',
  `host` varchar(255) NOT NULL COMMENT '主机名或 IP',
  `port` int NOT NULL COMMENT '端口',
  `database_name` varchar(128) NOT NULL COMMENT '数据库名',
  `username` varchar(128) NOT NULL COMMENT '登录用户名',
  `password_cipher` text NOT NULL COMMENT 'AES-GCM 密码密文',
  `jdbc_params` varchar(1000) DEFAULT NULL COMMENT '附加 JDBC 参数',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 启用，1 停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dp_data_source_code_deleted` (`code`, `deleted`),
  KEY `idx_dp_data_source_type_status` (`type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据中台数据源';

INSERT INTO `dp_data_source`
(`name`, `code`, `type`, `host`, `port`, `database_name`, `username`, `password_cipher`, `jdbc_params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
('2号人事部', 'twohao_hr_api', 'TWO_HAO_HR', 'https://openapi.2haohr.com', 443, 'corp_id', 'app_id', '',
 '{"syncObjects":["departments","employees"],"pageSize":100,"maxPages":10}', 1,
 '2号人事部 OpenAPI 数据源占位。编辑后填写企业 ID、应用 ID 和应用密钥，再启用。', 'system', NOW(), 'system', NOW(), b'0')
ON DUPLICATE KEY UPDATE `id` = `id`;

CREATE TABLE IF NOT EXISTS `dp_sync_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '同步任务编号',
  `name` varchar(100) NOT NULL COMMENT '任务名称',
  `code` varchar(64) NOT NULL COMMENT '任务唯一编码',
  `source_data_source_id` bigint NOT NULL COMMENT '源数据源编号',
  `source_sql` text NOT NULL COMMENT 'SeaTunnel JDBC Source 查询 SQL',
  `target_data_source_id` bigint NOT NULL COMMENT '目标 Doris 数据源编号',
  `target_database` varchar(64) NOT NULL COMMENT '目标数仓分层数据库',
  `target_table` varchar(128) NOT NULL COMMENT '目标表',
  `sink_sql` text NOT NULL COMMENT 'SeaTunnel JDBC Sink 写入 SQL',
  `mapping_config` longtext DEFAULT NULL COMMENT '字段映射 JSON 配置',
  `sync_mode` varchar(16) NOT NULL COMMENT 'FULL/INCREMENTAL',
  `watermark_column` varchar(128) DEFAULT NULL COMMENT '增量水位字段',
  `watermark_value` varchar(500) DEFAULT NULL COMMENT '最近成功水位',
  `parallelism` int NOT NULL DEFAULT 1 COMMENT 'SeaTunnel 并行度',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 启用，1 停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dp_sync_job_code_deleted` (`code`, `deleted`),
  KEY `idx_dp_sync_job_source` (`source_data_source_id`),
  KEY `idx_dp_sync_job_target` (`target_data_source_id`),
  KEY `idx_dp_sync_job_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据同步任务';

SET @dp_mapping_column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_sync_job' AND COLUMN_NAME = 'mapping_config'
);
SET @dp_mapping_ddl := IF(@dp_mapping_column_exists = 0,
  'ALTER TABLE `dp_sync_job` ADD COLUMN `mapping_config` longtext DEFAULT NULL COMMENT ''字段映射 JSON 配置'' AFTER `sink_sql`',
  'SELECT 1');
PREPARE dp_mapping_stmt FROM @dp_mapping_ddl;
EXECUTE dp_mapping_stmt;
DEALLOCATE PREPARE dp_mapping_stmt;

CREATE TABLE IF NOT EXISTS `dp_job_run` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '运行实例编号',
  `job_id` bigint NOT NULL COMMENT '同步任务编号',
  `job_name` varchar(100) NOT NULL COMMENT '任务名称快照',
  `batch_id` varchar(128) NOT NULL COMMENT '统一批次编号',
  `trigger_type` varchar(16) NOT NULL COMMENT '触发方式',
  `status` varchar(16) NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `exit_code` int DEFAULT NULL COMMENT 'SeaTunnel 进程退出码',
  `log_path` varchar(1000) DEFAULT NULL COMMENT '脱敏运行日志路径',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '错误摘要，不含凭据',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '触发用户',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dp_job_run_batch_id` (`batch_id`),
  KEY `idx_dp_job_run_job_time` (`job_id`, `create_time`),
  KEY `idx_dp_job_run_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据同步运行日志';

CREATE TABLE IF NOT EXISTS `dp_metadata_table` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '元数据表编号',
  `data_source_id` bigint NOT NULL COMMENT '数据源编号',
  `source_schema` varchar(128) NOT NULL COMMENT '源 Schema',
  `source_table` varchar(128) NOT NULL COMMENT '源表名',
  `business_name` varchar(128) DEFAULT NULL COMMENT '业务中文名',
  `business_domain` varchar(64) DEFAULT NULL COMMENT '业务域',
  `description` varchar(1000) DEFAULT NULL COMMENT '业务说明',
  `target_database` varchar(64) DEFAULT NULL COMMENT '目标数仓分层',
  `target_table` varchar(128) DEFAULT NULL COMMENT '目标数仓表',
  `field_count` int NOT NULL DEFAULT 0 COMMENT '字段数',
  `commented_field_count` int NOT NULL DEFAULT 0 COMMENT '源备注字段数',
  `definition_status` varchar(16) NOT NULL DEFAULT 'GENERATED' COMMENT '定义状态：GENERATED/CONFIRMED',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '发现状态：0 有效，1 已失效',
  `last_scan_time` datetime DEFAULT NULL COMMENT '最近扫描时间',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dp_metadata_table_source` (`data_source_id`, `source_schema`, `source_table`, `deleted`),
  KEY `idx_dp_metadata_table_source_status` (`data_source_id`, `status`),
  KEY `idx_dp_metadata_table_domain` (`business_domain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务表数据字典';

CREATE TABLE IF NOT EXISTS `dp_metadata_field` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '元数据字段编号',
  `metadata_table_id` bigint NOT NULL COMMENT '元数据表编号',
  `source_column` varchar(128) NOT NULL COMMENT '源字段名',
  `target_column` varchar(128) DEFAULT NULL COMMENT '目标字段名',
  `data_type` varchar(128) NOT NULL COMMENT '源数据类型',
  `jdbc_type` int DEFAULT NULL COMMENT 'JDBC 类型编码',
  `column_size` int DEFAULT NULL COMMENT '字段长度',
  `decimal_digits` int DEFAULT NULL COMMENT '小数位数',
  `nullable` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否可空',
  `primary_key` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否主键',
  `ordinal_position` int NOT NULL COMMENT '字段顺序',
  `source_comment` varchar(1000) DEFAULT NULL COMMENT '源库字段备注',
  `business_name` varchar(128) DEFAULT NULL COMMENT '业务中文名',
  `description` varchar(1000) DEFAULT NULL COMMENT '业务口径说明',
  `classification` varchar(64) DEFAULT NULL COMMENT '数据分类',
  `sensitivity_level` varchar(16) NOT NULL DEFAULT 'INTERNAL' COMMENT '敏感等级：PUBLIC/INTERNAL/SENSITIVE/RESTRICTED',
  `incremental_candidate` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否适合作为增量时间字段',
  `definition_status` varchar(16) NOT NULL DEFAULT 'GENERATED' COMMENT '定义状态：GENERATED/CONFIRMED',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '发现状态：0 有效，1 已失效',
  `last_scan_time` datetime DEFAULT NULL COMMENT '最近扫描时间',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dp_metadata_field_source` (`metadata_table_id`, `source_column`, `deleted`),
  KEY `idx_dp_metadata_field_table_status` (`metadata_table_id`, `status`),
  KEY `idx_dp_metadata_field_definition` (`definition_status`),
  KEY `idx_dp_metadata_field_sensitivity` (`sensitivity_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务字段数据字典';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(920000, '数据中台', '', 1, 20, 0, '/data-platform', 'ep:coin', NULL, NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920001, '数据源管理', 'data-platform:datasource:query', 2, 1, 920000, 'datasource', 'ep:connection', 'data-platform/datasource/index', 'DataPlatformDatasource', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920002, '数据同步', 'data-platform:sync-job:query', 2, 2, 920000, 'sync-job', 'ep:refresh', 'data-platform/sync-job/index', 'DataPlatformSyncJob', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920003, '数仓管理', 'data-platform:warehouse:query', 2, 3, 920000, 'warehouse', 'ep:coin', 'data-platform/warehouse/index', 'DataPlatformWarehouse', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920004, '运行日志', 'data-platform:job-log:query', 2, 5, 920000, 'job-log', 'ep:document', 'data-platform/job-log/index', 'DataPlatformJobLog', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920005, '数据字典', 'data-platform:data-dictionary:query', 2, 4, 920000, 'data-dictionary', 'ep:notebook', 'data-platform/data-dictionary/index', 'DataPlatformDataDictionary', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920010, '数据源查询', 'data-platform:datasource:query', 3, 1, 920001, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920011, '数据源创建', 'data-platform:datasource:create', 3, 2, 920001, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920012, '数据源修改', 'data-platform:datasource:update', 3, 3, 920001, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920013, '数据源删除', 'data-platform:datasource:delete', 3, 4, 920001, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920020, '同步任务查询', 'data-platform:sync-job:query', 3, 1, 920002, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920021, '同步任务创建', 'data-platform:sync-job:create', 3, 2, 920002, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920022, '同步任务修改', 'data-platform:sync-job:update', 3, 3, 920002, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920023, '同步任务删除', 'data-platform:sync-job:delete', 3, 4, 920002, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920024, '同步任务执行', 'data-platform:sync-job:execute', 3, 5, 920002, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920030, '数仓查询', 'data-platform:warehouse:query', 3, 1, 920003, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920040, '运行日志查询', 'data-platform:job-log:query', 3, 1, 920004, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920050, '数据字典查询', 'data-platform:data-dictionary:query', 3, 1, 920005, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920051, '数据字典扫描', 'data-platform:data-dictionary:refresh', 3, 2, 920005, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920052, '数据字典维护', 'data-platform:data-dictionary:update', 3, 3, 920005, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0')
ON DUPLICATE KEY UPDATE `id` = `id`;

UPDATE `system_menu` SET `sort` = 5, `update_time` = NOW(), `updater` = 'system' WHERE `id` = 920004;

-- Single-tenant deployment: retain yudao's fixed tenant_id=1 compatibility,
-- but disable all tenant and tenant-package menus in the administration UI.
UPDATE `system_menu`
SET `status` = 1, `visible` = b'0', `update_time` = NOW(), `updater` = 'system'
WHERE `id` BETWEEN 910200 AND 910223;

-- Minimal platform profile: hide legacy organization, BPM and AI custom menus.
-- User/role/menu administration plus operation/login logs remain available.
UPDATE `system_menu`
SET `status` = 1, `visible` = b'0', `update_time` = NOW(), `updater` = 'system'
WHERE `id` BETWEEN 910000 AND 910024
   OR `id` BETWEEN 910400 AND 910501
   OR `id` BETWEEN 910600 AND 910660
   OR `id` BETWEEN 920600 AND 920799;
