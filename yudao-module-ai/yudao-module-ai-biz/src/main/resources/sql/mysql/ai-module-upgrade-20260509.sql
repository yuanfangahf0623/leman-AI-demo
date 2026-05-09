-- AI 知识库模块字段升级脚本
-- 适用于已经创建过 ai_knowledge_base 的开发库；新库可直接执行 ai-module.sql。

ALTER TABLE `ai_knowledge_base`
  ADD COLUMN `visibility` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'public'
    COMMENT '可见范围：private 私有、team 团队可见、public 公开' AFTER `status`,
  ADD COLUMN `chat_model` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT 'Chat 模型' AFTER `embedding_model`,
  ADD COLUMN `score_threshold` double NOT NULL DEFAULT 0.7
    COMMENT '默认相似度阈值' AFTER `top_k`;
