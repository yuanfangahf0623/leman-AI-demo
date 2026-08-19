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

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(920000, '数据中台', '', 1, 20, 0, '/data-platform', 'ep:coin', NULL, NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920001, '数据源管理', 'data-platform:datasource:query', 2, 1, 920000, 'datasource', 'ep:connection', 'data-platform/datasource/index', 'DataPlatformDatasource', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920002, '数据同步', 'data-platform:sync-job:query', 2, 2, 920000, 'sync-job', 'ep:refresh', 'data-platform/sync-job/index', 'DataPlatformSyncJob', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920003, '数仓管理', 'data-platform:warehouse:query', 2, 3, 920000, 'warehouse', 'ep:coin', 'data-platform/warehouse/index', 'DataPlatformWarehouse', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(920004, '运行日志', 'data-platform:job-log:query', 2, 4, 920000, 'job-log', 'ep:document', 'data-platform/job-log/index', 'DataPlatformJobLog', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
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
(920040, '运行日志查询', 'data-platform:job-log:query', 3, 1, 920004, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0')
ON DUPLICATE KEY UPDATE `id` = `id`;

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
