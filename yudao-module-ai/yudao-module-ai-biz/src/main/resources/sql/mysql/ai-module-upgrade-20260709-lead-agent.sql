CREATE TABLE IF NOT EXISTS `ai_lead_market_category` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    `category_code` VARCHAR(64) NOT NULL COMMENT 'Stable market category code',
    `category_name` VARCHAR(128) NOT NULL COMMENT 'Market category name',
    `weight` INT NOT NULL DEFAULT 10 COMMENT 'Search allocation weight',
    `enabled` BIT NOT NULL DEFAULT b'1' COMMENT 'Whether this category is enabled',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT b'0'
) COMMENT 'AI lead agent market category';

CREATE TABLE IF NOT EXISTS `ai_lead_market_country` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    `category_id` BIGINT NOT NULL COMMENT 'Market category id',
    `country` VARCHAR(128) NOT NULL COMMENT 'Target country',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT b'0'
) COMMENT 'AI lead agent market country';

CREATE TABLE IF NOT EXISTS `ai_lead_market_keyword` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    `category_id` BIGINT NOT NULL COMMENT 'Market category id',
    `keyword` VARCHAR(255) NOT NULL COMMENT 'Search keyword',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT b'0'
) COMMENT 'AI lead agent market keyword';

CREATE TABLE IF NOT EXISTS `ai_lead_filter_rule` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    `rule_type` VARCHAR(64) NOT NULL COMMENT 'BLOCKED_DOMAIN_KEYWORD / BLOCKED_FILE_EXTENSION',
    `rule_value` VARCHAR(255) NOT NULL COMMENT 'Rule value',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    `enabled` BIT NOT NULL DEFAULT b'1' COMMENT 'Whether this rule is enabled',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT b'0'
) COMMENT 'AI lead agent filter rule';

CREATE TABLE IF NOT EXISTS `ai_lead_export_rule` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    `min_score` INT NOT NULL DEFAULT 0 COMMENT 'Minimum score to export',
    `include_target_only` BIT NOT NULL DEFAULT b'0' COMMENT 'Export target leads only',
    `require_email` BIT NOT NULL DEFAULT b'0' COMMENT 'Require best email',
    `allowed_grades` VARCHAR(32) NOT NULL DEFAULT 'A,B,C,D' COMMENT 'Allowed grades',
    `include_possible_duplicates` BIT NOT NULL DEFAULT b'1' COMMENT 'Include possible duplicates',
    `write_rejected_file` BIT NOT NULL DEFAULT b'1' COMMENT 'Write rejected file when exporting',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT b'0'
) COMMENT 'AI lead agent export rule';

CREATE TABLE IF NOT EXISTS `ai_lead_customer` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    `run_id` VARCHAR(64) NULL COMMENT 'Collection run id',
    `company_name` VARCHAR(255) NULL COMMENT 'Company name',
    `country` VARCHAR(128) NULL COMMENT 'Country',
    `website` VARCHAR(1024) NOT NULL COMMENT 'Website URL',
    `domain` VARCHAR(255) NOT NULL COMMENT 'Website domain',
    `emails_json` JSON NULL COMMENT 'All public business emails',
    `best_email` VARCHAR(255) NULL COMMENT 'Best contact email',
    `email_type` VARCHAR(64) NULL COMMENT 'Email type',
    `phone` VARCHAR(128) NULL COMMENT 'Phone',
    `contact_page` VARCHAR(1024) NULL COMMENT 'Contact page URL',
    `about_page` VARCHAR(1024) NULL COMMENT 'About page URL',
    `main_products_json` JSON NULL COMMENT 'Main products',
    `is_target` BIT NULL COMMENT 'Whether this is a target lead',
    `matched_category` VARCHAR(64) NULL COMMENT 'Matched market category',
    `target_customer_type` VARCHAR(128) NULL COMMENT 'Target customer type',
    `score` INT NOT NULL DEFAULT 0 COMMENT 'Lead score',
    `score_reason` TEXT NULL COMMENT 'Score reason',
    `source_url` VARCHAR(1024) NULL COMMENT 'Source URL',
    `collected_at` DATETIME NULL COMMENT 'Collected time',
    `compliance_note` VARCHAR(1024) NULL COMMENT 'Compliance note',
    `development_email_subject` VARCHAR(512) NULL COMMENT 'Generated outreach email subject',
    `development_email_body` MEDIUMTEXT NULL COMMENT 'Generated outreach email body',
    `duplicate_status` VARCHAR(64) NOT NULL DEFAULT 'unique' COMMENT 'Deduplication status',
    `crawl_errors_json` JSON NULL COMMENT 'Crawl errors',
    `analysis_provider` VARCHAR(128) NOT NULL DEFAULT 'rules' COMMENT 'Analysis provider',
    `ai_review_error` VARCHAR(1024) NULL COMMENT 'AI review error',
    `social_profiles_json` JSON NULL COMMENT 'Social profiles',
    `social_activity_summary` VARCHAR(1024) NULL COMMENT 'Social activity summary',
    `social_verification_score` INT NOT NULL DEFAULT 0 COMMENT 'Social verification score',
    `social_verification_reason` VARCHAR(1024) NULL COMMENT 'Social verification reason',
    `external_appearance_count` INT NOT NULL DEFAULT 0 COMMENT 'External appearance count',
    `external_appearance_urls_json` JSON NULL COMMENT 'External appearance URLs',
    `demo_status` VARCHAR(64) NULL COMMENT 'Demo status',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT b'0'
) COMMENT 'AI lead agent customer';

CREATE TABLE IF NOT EXISTS `ai_lead_crawl_history` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'Tenant id',
    `run_id` VARCHAR(64) NOT NULL COMMENT 'Collection run id',
    `search_provider` VARCHAR(64) NULL COMMENT 'Search provider',
    `search_category` VARCHAR(64) NULL COMMENT 'Search category',
    `search_country` VARCHAR(128) NULL COMMENT 'Search country',
    `search_keyword` VARCHAR(255) NULL COMMENT 'Search keyword',
    `website` VARCHAR(1024) NULL COMMENT 'Candidate website',
    `domain` VARCHAR(255) NULL COMMENT 'Candidate domain',
    `crawl_status` VARCHAR(64) NULL COMMENT 'Crawl status',
    `pages_crawled` INT NOT NULL DEFAULT 0 COMMENT 'Pages crawled',
    `crawled_pages_json` JSON NULL COMMENT 'Crawled page URLs',
    `crawl_errors_json` JSON NULL COMMENT 'Crawl errors',
    `classification_reason` TEXT NULL COMMENT 'Classification reason',
    `is_target` BIT NULL COMMENT 'Whether this is target',
    `score` INT NOT NULL DEFAULT 0 COMMENT 'Lead score',
    `customer_id` BIGINT NULL COMMENT 'Linked customer id',
    `raw_json` JSON NULL COMMENT 'Original history payload',
    `collected_at` DATETIME NULL COMMENT 'Collected time',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT b'0'
) COMMENT 'AI lead agent crawl history';

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_lead_market_category'
      AND INDEX_NAME = 'uk_ai_lead_market_category_code'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_ai_lead_market_category_code ON ai_lead_market_category (tenant_id, category_code, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_lead_market_country'
      AND INDEX_NAME = 'uk_ai_lead_market_country'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_ai_lead_market_country ON ai_lead_market_country (tenant_id, category_id, country, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_lead_market_keyword'
      AND INDEX_NAME = 'uk_ai_lead_market_keyword'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_ai_lead_market_keyword ON ai_lead_market_keyword (tenant_id, category_id, keyword, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_lead_filter_rule'
      AND INDEX_NAME = 'uk_ai_lead_filter_rule'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_ai_lead_filter_rule ON ai_lead_filter_rule (tenant_id, rule_type, rule_value, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_lead_export_rule'
      AND INDEX_NAME = 'uk_ai_lead_export_rule_tenant'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_ai_lead_export_rule_tenant ON ai_lead_export_rule (tenant_id, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_lead_customer'
      AND INDEX_NAME = 'uk_ai_lead_customer_domain_run'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE UNIQUE INDEX uk_ai_lead_customer_domain_run ON ai_lead_customer (tenant_id, domain, run_id, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_lead_customer'
      AND INDEX_NAME = 'idx_ai_lead_customer_score'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_ai_lead_customer_score ON ai_lead_customer (tenant_id, score, id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_lead_customer'
      AND INDEX_NAME = 'idx_ai_lead_customer_category'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_ai_lead_customer_category ON ai_lead_customer (tenant_id, matched_category, score)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_lead_crawl_history'
      AND INDEX_NAME = 'idx_ai_lead_history_run'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_ai_lead_history_run ON ai_lead_crawl_history (tenant_id, run_id, id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_lead_crawl_history'
      AND INDEX_NAME = 'idx_ai_lead_history_domain'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_ai_lead_history_domain ON ai_lead_crawl_history (tenant_id, domain, id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `ai_lead_market_category`
(`tenant_id`, `category_code`, `category_name`, `weight`, `enabled`, `creator`, `updater`)
VALUES
(0, 'yacht_and_marine', 'Yacht and Marine', 100, b'1', 'admin', 'admin'),
(0, 'camper_and_rv', 'Camper and RV', 90, b'1', 'admin', 'admin'),
(0, 'appliance_dealers', 'Appliance Dealers', 85, b'1', 'admin', 'admin'),
(0, 'wall_mounted_washing_machine', 'Wall Mounted Washing Machine', 25, b'1', 'admin', 'admin'),
(0, 'hotel_and_serviced_apartment', 'Hotel and Serviced Apartment', 20, b'1', 'admin', 'admin'),
(0, 'real_estate_and_interior', 'Real Estate and Interior', 25, b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `category_name` = VALUES(`category_name`),
    `weight` = VALUES(`weight`),
    `enabled` = VALUES(`enabled`),
    `deleted` = b'0';

INSERT INTO `ai_lead_market_country` (`tenant_id`, `category_id`, `country`, `sort_order`, `creator`, `updater`)
SELECT 0, `id`, `country`, `sort_order`, 'admin', 'admin'
FROM `ai_lead_market_category`
JOIN (
    SELECT 'yacht_and_marine' AS `category_code`, 'Germany' AS `country`, 1 AS `sort_order` UNION ALL
    SELECT 'yacht_and_marine', 'France', 2 UNION ALL
    SELECT 'yacht_and_marine', 'Italy', 3 UNION ALL
    SELECT 'yacht_and_marine', 'Spain', 4 UNION ALL
    SELECT 'yacht_and_marine', 'Netherlands', 5 UNION ALL
    SELECT 'yacht_and_marine', 'Croatia', 6 UNION ALL
    SELECT 'yacht_and_marine', 'Greece', 7 UNION ALL
    SELECT 'yacht_and_marine', 'United Kingdom', 8 UNION ALL
    SELECT 'yacht_and_marine', 'Monaco', 9 UNION ALL
    SELECT 'camper_and_rv', 'Germany', 1 UNION ALL
    SELECT 'camper_and_rv', 'France', 2 UNION ALL
    SELECT 'camper_and_rv', 'Italy', 3 UNION ALL
    SELECT 'camper_and_rv', 'Spain', 4 UNION ALL
    SELECT 'camper_and_rv', 'Netherlands', 5 UNION ALL
    SELECT 'camper_and_rv', 'Poland', 6 UNION ALL
    SELECT 'camper_and_rv', 'Czech Republic', 7 UNION ALL
    SELECT 'camper_and_rv', 'United Kingdom', 8 UNION ALL
    SELECT 'camper_and_rv', 'Austria', 9 UNION ALL
    SELECT 'appliance_dealers', 'Germany', 1 UNION ALL
    SELECT 'appliance_dealers', 'France', 2 UNION ALL
    SELECT 'appliance_dealers', 'Italy', 3 UNION ALL
    SELECT 'appliance_dealers', 'Spain', 4 UNION ALL
    SELECT 'appliance_dealers', 'Netherlands', 5 UNION ALL
    SELECT 'appliance_dealers', 'Poland', 6 UNION ALL
    SELECT 'appliance_dealers', 'Czech Republic', 7 UNION ALL
    SELECT 'appliance_dealers', 'United Kingdom', 8 UNION ALL
    SELECT 'appliance_dealers', 'Belgium', 9 UNION ALL
    SELECT 'appliance_dealers', 'Sweden', 10 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'Germany', 1 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'France', 2 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'Italy', 3 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'Spain', 4 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'Netherlands', 5 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'Poland', 6 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'Czech Republic', 7 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'United Kingdom', 8 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'Germany', 1 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'France', 2 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'Italy', 3 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'Spain', 4 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'Netherlands', 5 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'Poland', 6 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'Czech Republic', 7 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'United Kingdom', 8 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'Austria', 9 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'Switzerland', 10 UNION ALL
    SELECT 'real_estate_and_interior', 'Germany', 1 UNION ALL
    SELECT 'real_estate_and_interior', 'France', 2 UNION ALL
    SELECT 'real_estate_and_interior', 'Italy', 3 UNION ALL
    SELECT 'real_estate_and_interior', 'Spain', 4 UNION ALL
    SELECT 'real_estate_and_interior', 'Netherlands', 5 UNION ALL
    SELECT 'real_estate_and_interior', 'Poland', 6 UNION ALL
    SELECT 'real_estate_and_interior', 'Czech Republic', 7 UNION ALL
    SELECT 'real_estate_and_interior', 'United Kingdom', 8 UNION ALL
    SELECT 'real_estate_and_interior', 'Belgium', 9 UNION ALL
    SELECT 'real_estate_and_interior', 'Sweden', 10
) seed ON seed.`category_code` = `ai_lead_market_category`.`category_code`
WHERE `ai_lead_market_category`.`tenant_id` = 0 AND `ai_lead_market_category`.`deleted` = b'0'
ON DUPLICATE KEY UPDATE
    `sort_order` = VALUES(`sort_order`),
    `deleted` = b'0';

INSERT INTO `ai_lead_market_keyword` (`tenant_id`, `category_id`, `keyword`, `sort_order`, `creator`, `updater`)
SELECT 0, `id`, `keyword`, `sort_order`, 'admin', 'admin'
FROM `ai_lead_market_category`
JOIN (
    SELECT 'yacht_and_marine' AS `category_code`, 'yacht sales company Europe' AS `keyword`, 1 AS `sort_order` UNION ALL
    SELECT 'yacht_and_marine', 'luxury yacht dealer Europe contact email', 2 UNION ALL
    SELECT 'yacht_and_marine', 'yacht broker company Europe', 3 UNION ALL
    SELECT 'yacht_and_marine', 'yacht sales company Germany', 4 UNION ALL
    SELECT 'yacht_and_marine', 'yacht sales company France', 5 UNION ALL
    SELECT 'yacht_and_marine', 'yacht manufacturer Europe purchasing', 6 UNION ALL
    SELECT 'yacht_and_marine', 'yacht club supplier partnership Europe', 7 UNION ALL
    SELECT 'yacht_and_marine', 'yacht maintenance company Europe', 8 UNION ALL
    SELECT 'yacht_and_marine', 'yacht equipment supplier Europe', 9 UNION ALL
    SELECT 'yacht_and_marine', 'boat equipment supplier Europe', 10 UNION ALL
    SELECT 'yacht_and_marine', 'marine equipment company Europe', 11 UNION ALL
    SELECT 'yacht_and_marine', 'luxury yacht fitting supplier Europe', 12 UNION ALL
    SELECT 'yacht_and_marine', 'houseboat manufacturer Europe', 13 UNION ALL
    SELECT 'yacht_and_marine', 'ship interior fit out company Europe', 14 UNION ALL
    SELECT 'camper_and_rv', 'motorhome sales company Europe', 1 UNION ALL
    SELECT 'camper_and_rv', 'RV sales company Europe contact email', 2 UNION ALL
    SELECT 'camper_and_rv', 'camper van dealer Europe', 3 UNION ALL
    SELECT 'camper_and_rv', 'caravan dealer Germany', 4 UNION ALL
    SELECT 'camper_and_rv', 'motorhome dealer France', 5 UNION ALL
    SELECT 'camper_and_rv', 'RV manufacturer Europe purchasing', 6 UNION ALL
    SELECT 'camper_and_rv', 'camper van conversion company Europe', 7 UNION ALL
    SELECT 'camper_and_rv', 'caravan accessories supplier Europe', 8 UNION ALL
    SELECT 'camper_and_rv', 'RV accessories supplier Europe', 9 UNION ALL
    SELECT 'camper_and_rv', 'campsite operator Europe procurement', 10 UNION ALL
    SELECT 'camper_and_rv', 'outdoor equipment company Europe', 11 UNION ALL
    SELECT 'camper_and_rv', 'camping equipment supplier Europe', 12 UNION ALL
    SELECT 'appliance_dealers', 'home appliance dealer Europe', 1 UNION ALL
    SELECT 'appliance_dealers', 'home appliance distributor Europe contact email', 2 UNION ALL
    SELECT 'appliance_dealers', 'home appliance wholesaler Europe', 3 UNION ALL
    SELECT 'appliance_dealers', 'home appliance importer Europe', 4 UNION ALL
    SELECT 'appliance_dealers', 'home appliance agent Europe', 5 UNION ALL
    SELECT 'appliance_dealers', 'home appliance retailer Germany', 6 UNION ALL
    SELECT 'appliance_dealers', 'kitchen and bathroom appliance store Europe', 7 UNION ALL
    SELECT 'appliance_dealers', 'electrical appliance chain store Europe', 8 UNION ALL
    SELECT 'appliance_dealers', 'smart home company Europe appliance', 9 UNION ALL
    SELECT 'appliance_dealers', 'home goods importer Europe appliance', 10 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'wall mounted washing machine shop Europe', 1 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'wall mounted washer distributor Europe', 2 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'mini washing machine retailer Germany', 3 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'compact washing machine distributor Europe', 4 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'small appliance store wall mounted washing machine', 5 UNION ALL
    SELECT 'wall_mounted_washing_machine', 'compact laundry appliance supplier Europe', 6 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'boutique hotel procurement Europe', 1 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'hotel chain purchasing department Europe', 2 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'high end guesthouse Europe contact email', 3 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'serviced apartment operator Europe', 4 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'long stay apartment operator Europe', 5 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'hotel supplies supplier Europe', 6 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'hotel equipment purchasing company Europe', 7 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'hotel engineering company Europe', 8 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'hotel renovation company Europe', 9 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'hotel smart room solution company Europe', 10 UNION ALL
    SELECT 'hotel_and_serviced_apartment', 'guest room supplies supplier Europe', 11 UNION ALL
    SELECT 'real_estate_and_interior', 'apartment developer Europe', 1 UNION ALL
    SELECT 'real_estate_and_interior', 'real estate developer furnished apartments Europe', 2 UNION ALL
    SELECT 'real_estate_and_interior', 'furnished apartment company Europe', 3 UNION ALL
    SELECT 'real_estate_and_interior', 'interior design company Europe apartment', 4 UNION ALL
    SELECT 'real_estate_and_interior', 'home renovation company Europe', 5 UNION ALL
    SELECT 'real_estate_and_interior', 'modular housing company Europe', 6 UNION ALL
    SELECT 'real_estate_and_interior', 'container housing company Europe', 7 UNION ALL
    SELECT 'real_estate_and_interior', 'tiny house company Europe', 8
) seed ON seed.`category_code` = `ai_lead_market_category`.`category_code`
WHERE `ai_lead_market_category`.`tenant_id` = 0 AND `ai_lead_market_category`.`deleted` = b'0'
ON DUPLICATE KEY UPDATE
    `sort_order` = VALUES(`sort_order`),
    `deleted` = b'0';

INSERT INTO `ai_lead_filter_rule` (`tenant_id`, `rule_type`, `rule_value`, `sort_order`, `enabled`, `creator`, `updater`)
SELECT 0, 'BLOCKED_DOMAIN_KEYWORD', `value`, `sort_order`, b'1', 'admin', 'admin'
FROM (
    SELECT 'amazon' AS `value`, 1 AS `sort_order` UNION ALL SELECT 'ebay', 2 UNION ALL
    SELECT 'aliexpress', 3 UNION ALL SELECT 'alibaba', 4 UNION ALL SELECT 'temu', 5 UNION ALL
    SELECT 'ubuy', 6 UNION ALL SELECT 'apple', 7 UNION ALL SELECT 'sciencedirect', 8 UNION ALL
    SELECT 'snsinsider', 9 UNION ALL SELECT 'noon', 10 UNION ALL SELECT 'fruugo', 11 UNION ALL
    SELECT 'kiwicollection', 12 UNION ALL SELECT 'fitchratings', 13 UNION ALL SELECT 'facebook', 14 UNION ALL
    SELECT 'instagram', 15 UNION ALL SELECT 'linkedin', 16 UNION ALL SELECT 'youtube', 17 UNION ALL
    SELECT 'youtu', 18 UNION ALL SELECT 'tiktok', 19 UNION ALL SELECT 'pinterest', 20 UNION ALL
    SELECT 'wikipedia', 21 UNION ALL SELECT 'google', 22 UNION ALL SELECT 'bing', 23 UNION ALL
    SELECT 'reddit', 24 UNION ALL SELECT 'quora', 25 UNION ALL SELECT 'trustpilot', 26 UNION ALL
    SELECT 'tripadvisor', 27 UNION ALL SELECT 'yelp', 28
) seed
ON DUPLICATE KEY UPDATE
    `sort_order` = VALUES(`sort_order`),
    `enabled` = b'1',
    `deleted` = b'0';

INSERT INTO `ai_lead_filter_rule` (`tenant_id`, `rule_type`, `rule_value`, `sort_order`, `enabled`, `creator`, `updater`)
SELECT 0, 'BLOCKED_FILE_EXTENSION', `value`, `sort_order`, b'1', 'admin', 'admin'
FROM (
    SELECT '.pdf' AS `value`, 1 AS `sort_order` UNION ALL SELECT '.jpg', 2 UNION ALL
    SELECT '.jpeg', 3 UNION ALL SELECT '.png', 4 UNION ALL SELECT '.gif', 5 UNION ALL
    SELECT '.webp', 6 UNION ALL SELECT '.svg', 7 UNION ALL SELECT '.mp4', 8 UNION ALL
    SELECT '.mov', 9 UNION ALL SELECT '.avi', 10 UNION ALL SELECT '.zip', 11 UNION ALL
    SELECT '.rar', 12
) seed
ON DUPLICATE KEY UPDATE
    `sort_order` = VALUES(`sort_order`),
    `enabled` = b'1',
    `deleted` = b'0';

INSERT INTO `ai_lead_export_rule`
(`tenant_id`, `min_score`, `include_target_only`, `require_email`, `allowed_grades`,
 `include_possible_duplicates`, `write_rejected_file`, `creator`, `updater`)
VALUES
(0, 0, b'0', b'0', 'A,B,C,D', b'1', b'1', 'admin', 'admin')
ON DUPLICATE KEY UPDATE
    `deleted` = b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(910607, '线索采集', 'ai:lead-agent:query', 2, 7, 910600, 'lead-agent', 'ep:aim',
 'ai/lead-agent/index', 'AiLeadAgent', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910670, '线索采集查询', 'ai:lead-agent:query', 3, 1, 910607, '', '', '', NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910671, '线索采集配置', 'ai:lead-agent:update', 3, 2, 910607, '', '', '', NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
    `id` = `id`;
