-- yudao 管理后台菜单补充脚本。
-- 用途：把当前前端已开放的 yudao 原有页面写入 system_menu，供后端动态菜单接口返回。
-- 说明：
-- 1. 本脚本依赖 yudao-cloud / RuoYi 风格的 system_menu 表结构。
-- 2. 菜单 ID 使用 910000 号段，避免与 yudao 官方初始化数据冲突。
-- 3. 本脚本只维护菜单和按钮权限，不自动给角色授权；角色授权请在“角色管理”中分配。

CREATE TABLE IF NOT EXISTS `system_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜单名称',
  `permission` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '权限标识',
  `type` tinyint NOT NULL COMMENT '菜单类型',
  `sort` int NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父菜单ID',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '路由地址',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#' COMMENT '菜单图标',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '组件路径',
  `component_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '组件名',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '菜单状态',
  `visible` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否可见',
  `keep_alive` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否缓存',
  `always_show` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否总是显示',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_system_menu_parent_id` (`parent_id`),
  KEY `idx_system_menu_permission` (`permission`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '菜单权限表';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- 组织管理
(910000, '组织管理', '', 1, 11, 0, '/system/org', 'ep:office-building', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910001, '部门管理', '', 2, 1, 910000, 'dept', 'ep:office-building', 'system/dept/index', 'SystemDept', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910002, '岗位管理', '', 2, 2, 910000, 'post', 'ep:postcard', 'system/post/index', 'SystemPost', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 用户管理
(910100, '用户管理', '', 2, 1, 910300, 'user', 'ep:user', 'system/user/index', 'SystemUser', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 租户管理
(910200, '租户管理', '', 1, 13, 0, '/system/tenant', 'ep:management', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910201, '租户列表', '', 2, 1, 910200, 'list', 'ep:office-building', 'system/tenant/index', 'SystemTenant', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910202, '租户套餐', '', 2, 2, 910200, 'package', 'ep:collection-tag', 'system/tenantPackage/index', 'SystemTenantPackage', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 系统管理
(910300, '系统管理', '', 1, 14, 0, '/system', 'ep:setting', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910301, '角色管理', '', 2, 2, 910300, 'role', 'ep:user-filled', 'system/role/index', 'SystemRole', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910302, '菜单管理', '', 2, 3, 910300, 'menu', 'ep:menu', 'system/menu/index', 'SystemMenu', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910303, '字典管理', '', 2, 4, 910300, 'dict', 'ep:collection', 'system/dict/index', 'SystemDictType', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910304, '操作日志', '', 2, 5, 910300, 'operatelog', 'ep:document', 'system/operatelog/index', 'SystemOperateLog', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910305, '登录日志', '', 2, 6, 910300, 'loginlog', 'ep:monitor', 'system/loginlog/index', 'SystemLoginLog', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 工作流
(910400, '工作流', '', 1, 50, 0, '/bpm', 'ep:share', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910401, '发起流程', '', 2, 1, 910400, 'process-instance/create', 'ep:promotion', 'bpm/processInstance/create/index', 'BpmProcessInstanceCreate', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910402, '我的流程', '', 2, 2, 910400, 'process-instance/my', 'ep:list', 'bpm/processInstance/index', 'BpmProcessInstanceMy', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910403, '待办任务', '', 2, 3, 910400, 'task/my', 'ep:checked', 'bpm/task/todo/index', 'BpmTodoTask', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910404, '已办任务', '', 2, 4, 910400, 'task/done', 'ep:circle-check', 'bpm/task/done/index', 'BpmDoneTask', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910405, '抄送我的', 'bpm:process-instance-cc:query', 2, 5, 910400, 'task/copy', 'ep:message', 'bpm/task/copy/index', 'BpmProcessInstanceCopy', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910406, '流程模型', '', 2, 6, 910400, 'manager/model', 'ep:connection', 'bpm/model/index', 'BpmModel', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910407, '流程表单', '', 2, 7, 910400, 'manager/form', 'ep:document', 'bpm/form/index', 'BpmForm', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910408, '流程分类', '', 2, 8, 910400, 'manager/category', 'ep:folder-opened', 'bpm/category/index', 'BpmCategory', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910409, '用户组', '', 2, 9, 910400, 'manager/group', 'ep:user-filled', 'bpm/group/index', 'BpmUserGroup', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910410, '流程表达式', '', 2, 10, 910400, 'manager/expression', 'ep:operation', 'bpm/processExpression/index', 'BpmProcessExpression', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910411, '流程监听器', '', 2, 11, 910400, 'manager/listener', 'ep:bell', 'bpm/processListener/index', 'BpmProcessListener', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910412, '流程实例', '', 2, 12, 910400, 'process-instance/manager', 'ep:data-line', 'bpm/processInstance/manager/index', 'BpmProcessInstanceManager', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910413, '任务管理', '', 2, 13, 910400, 'task/manager', 'ep:finished', 'bpm/task/manager/index', 'BpmManagerTask', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910414, 'OA 请假', '', 2, 14, 910400, 'oa/leave', 'ep:calendar', 'bpm/oa/leave/index', 'BpmOALeave', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- AI 知识库
(910600, 'AI 知识库', '', 1, 10, 0, '/ai', 'ep:collection', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910601, '知识库管理', 'ai:knowledge:query', 2, 1, 910600, 'knowledge', 'ep:folder-opened', 'ai/knowledge-base/knowledge/index', 'AiKnowledgeManage', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910602, '文档管理', 'ai:document:query', 2, 2, 910600, 'document', 'ep:document', 'ai/knowledge-base/document/index', 'AiKnowledgeDocumentManage', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910603, '数据源管理', 'ai:datasource:query', 2, 3, 910600, 'datasource', 'ep:connection', 'ai/knowledge-base/datasource/index', 'AiKnowledgeDataSourceManage', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910604, '同步任务', 'ai:sync-job:query', 2, 4, 910600, 'sync-job', 'ep:refresh', 'ai/knowledge-base/sync-job/index', 'AiKnowledgeSyncJob', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910605, 'AI问答', 'ai:chat:test', 2, 5, 910600, 'chat-test', 'ep:chat-dot-round', 'ai/knowledge-base/chat-test/index', 'AiKnowledgeChatTest', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910606, '问答记录', 'ai:chat-record:query', 2, 6, 910600, 'chat-record', 'ep:chat-line-round', 'ai/knowledge-base/chat-record/index', 'AiKnowledgeChatRecord', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 组织管理按钮权限
(910010, '部门查询', 'system:dept:query', 3, 1, 910001, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910011, '部门新增', 'system:dept:create', 3, 2, 910001, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910012, '部门修改', 'system:dept:update', 3, 3, 910001, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910013, '部门删除', 'system:dept:delete', 3, 4, 910001, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910020, '岗位查询', 'system:post:query', 3, 1, 910002, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910021, '岗位新增', 'system:post:create', 3, 2, 910002, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910022, '岗位修改', 'system:post:update', 3, 3, 910002, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910023, '岗位删除', 'system:post:delete', 3, 4, 910002, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910024, '岗位导出', 'system:post:export', 3, 5, 910002, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 用户管理按钮权限
(910110, '用户查询', 'system:user:query', 3, 1, 910100, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910111, '用户新增', 'system:user:create', 3, 2, 910100, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910112, '用户修改', 'system:user:update', 3, 3, 910100, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910113, '用户删除', 'system:user:delete', 3, 4, 910100, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910114, '用户导出', 'system:user:export', 3, 5, 910100, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910115, '用户导入', 'system:user:import', 3, 6, 910100, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910116, '重置密码', 'system:user:update-password', 3, 7, 910100, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910117, '设置用户角色', 'system:permission:assign-user-role', 3, 8, 910100, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 租户管理按钮权限
(910210, '租户查询', 'system:tenant:query', 3, 1, 910201, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910211, '租户创建', 'system:tenant:create', 3, 2, 910201, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910212, '租户更新', 'system:tenant:update', 3, 3, 910201, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910213, '租户删除', 'system:tenant:delete', 3, 4, 910201, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910214, '租户导出', 'system:tenant:export', 3, 5, 910201, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910215, '租户切换', 'system:tenant:visit', 3, 6, 910201, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910220, '租户套餐查询', 'system:tenant-package:query', 3, 1, 910202, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910221, '租户套餐创建', 'system:tenant-package:create', 3, 2, 910202, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910222, '租户套餐更新', 'system:tenant-package:update', 3, 3, 910202, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910223, '租户套餐删除', 'system:tenant-package:delete', 3, 4, 910202, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 系统管理按钮权限
(910310, '角色查询', 'system:role:query', 3, 1, 910301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910311, '角色新增', 'system:role:create', 3, 2, 910301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910312, '角色修改', 'system:role:update', 3, 3, 910301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910313, '角色删除', 'system:role:delete', 3, 4, 910301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910314, '角色导出', 'system:role:export', 3, 5, 910301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910315, '设置角色菜单权限', 'system:permission:assign-role-menu', 3, 6, 910301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910316, '设置角色数据权限', 'system:permission:assign-role-data-scope', 3, 7, 910301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910320, '菜单查询', 'system:menu:query', 3, 1, 910302, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910321, '菜单新增', 'system:menu:create', 3, 2, 910302, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910322, '菜单修改', 'system:menu:update', 3, 3, 910302, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910323, '菜单删除', 'system:menu:delete', 3, 4, 910302, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910330, '字典查询', 'system:dict:query', 3, 1, 910303, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910331, '字典新增', 'system:dict:create', 3, 2, 910303, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910332, '字典修改', 'system:dict:update', 3, 3, 910303, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910333, '字典删除', 'system:dict:delete', 3, 4, 910303, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910334, '字典导出', 'system:dict:export', 3, 5, 910303, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910340, '操作日志查询', 'system:operate-log:query', 3, 1, 910304, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910341, '操作日志导出', 'system:operate-log:export', 3, 2, 910304, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910350, '登录日志查询', 'system:login-log:query', 3, 1, 910305, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910351, '登录日志导出', 'system:login-log:export', 3, 2, 910305, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 工作流按钮权限
(910420, '流程实例创建', 'bpm:process-instance:create', 3, 1, 910401, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910421, '流程实例查询', 'bpm:process-instance:query', 3, 1, 910402, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910422, '流程实例取消', 'bpm:process-instance:cancel', 3, 2, 910402, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910430, '模型查询', 'bpm:model:query', 3, 1, 910406, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910431, '模型创建', 'bpm:model:create', 3, 2, 910406, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910432, '模型更新', 'bpm:model:update', 3, 3, 910406, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910433, '模型删除', 'bpm:model:delete', 3, 4, 910406, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910434, '模型发布', 'bpm:model:deploy', 3, 5, 910406, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910435, '流程清理', 'bpm:model:clean', 3, 6, 910406, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910436, '流程定义查询', 'bpm:process-definition:query', 3, 7, 910406, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910440, '表单查询', 'bpm:form:query', 3, 1, 910407, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910441, '表单创建', 'bpm:form:create', 3, 2, 910407, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910442, '表单更新', 'bpm:form:update', 3, 3, 910407, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910443, '表单删除', 'bpm:form:delete', 3, 4, 910407, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910444, '表单导出', 'bpm:form:export', 3, 5, 910407, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910450, '分类查询', 'bpm:category:query', 3, 1, 910408, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910451, '分类创建', 'bpm:category:create', 3, 2, 910408, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910452, '分类更新', 'bpm:category:update', 3, 3, 910408, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910453, '分类删除', 'bpm:category:delete', 3, 4, 910408, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910460, '用户组查询', 'bpm:user-group:query', 3, 1, 910409, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910461, '用户组创建', 'bpm:user-group:create', 3, 2, 910409, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910462, '用户组更新', 'bpm:user-group:update', 3, 3, 910409, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910463, '用户组删除', 'bpm:user-group:delete', 3, 4, 910409, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910470, '流程表达式查询', 'bpm:process-expression:query', 3, 1, 910410, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910471, '流程表达式创建', 'bpm:process-expression:create', 3, 2, 910410, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910472, '流程表达式更新', 'bpm:process-expression:update', 3, 3, 910410, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910473, '流程表达式删除', 'bpm:process-expression:delete', 3, 4, 910410, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910480, '流程监听器查询', 'bpm:process-listener:query', 3, 1, 910411, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910481, '流程监听器创建', 'bpm:process-listener:create', 3, 2, 910411, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910482, '流程监听器更新', 'bpm:process-listener:update', 3, 3, 910411, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910483, '流程监听器删除', 'bpm:process-listener:delete', 3, 4, 910411, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910490, '流程实例查询（管理员）', 'bpm:process-instance:manager-query', 3, 1, 910412, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910491, '流程实例取消（管理员）', 'bpm:process-instance:cancel-by-admin', 3, 2, 910412, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910500, 'OA 请假查询', 'bpm:oa-leave:query', 3, 1, 910414, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910501, 'OA 请假创建', 'bpm:oa-leave:create', 3, 2, 910414, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- AI 知识库按钮权限
(910610, '知识库查询', 'ai:knowledge:query', 3, 1, 910601, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910611, '知识库新增', 'ai:knowledge:create', 3, 2, 910601, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910612, '知识库修改', 'ai:knowledge:update', 3, 3, 910601, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910613, '知识库删除', 'ai:knowledge:delete', 3, 4, 910601, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910620, '文档查询', 'ai:document:query', 3, 1, 910602, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910621, '文档上传', 'ai:document:upload', 3, 2, 910602, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910622, '文档解析', 'ai:document:parse', 3, 3, 910602, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910623, '文档向量化', 'ai:document:embed', 3, 4, 910602, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910624, '文档删除', 'ai:document:delete', 3, 5, 910602, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910625, '文档编辑', 'ai:document:update', 3, 6, 910602, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910630, '数据源查询', 'ai:datasource:query', 3, 1, 910603, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910631, '数据源新增', 'ai:datasource:create', 3, 2, 910603, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910632, '数据源修改', 'ai:datasource:update', 3, 3, 910603, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910633, '数据源删除', 'ai:datasource:delete', 3, 4, 910603, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910640, '同步任务查询', 'ai:sync-job:query', 3, 1, 910604, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910641, '同步任务创建', 'ai:sync-job:create', 3, 2, 910604, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910642, '同步任务执行', 'ai:sync-job:execute', 3, 3, 910604, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910650, 'AI问答', 'ai:chat:test', 3, 1, 910605, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(910660, '问答记录查询', 'ai:chat-record:query', 3, 1, 910606, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `id` = `id`;

-- 菜单迁移：用户管理从一级菜单移动到系统管理下。
-- 因为上面的 INSERT 遇到已存在菜单时不会覆盖用户在页面上的配置，这里显式同步基础归属和排序。
UPDATE `system_menu`
SET `parent_id` = 910300,
    `sort` = 1,
    `path` = 'user',
    `component` = 'system/user/index',
    `component_name` = 'SystemUser',
    `updater` = 'admin',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `id` = 910100;

UPDATE `system_menu` SET `sort` = 2, `updater` = 'admin', `update_time` = NOW() WHERE `id` = 910301;
UPDATE `system_menu` SET `sort` = 3, `updater` = 'admin', `update_time` = NOW() WHERE `id` = 910302;
UPDATE `system_menu` SET `sort` = 4, `updater` = 'admin', `update_time` = NOW() WHERE `id` = 910303;
UPDATE `system_menu` SET `sort` = 5, `updater` = 'admin', `update_time` = NOW() WHERE `id` = 910304;
UPDATE `system_menu` SET `sort` = 6, `updater` = 'admin', `update_time` = NOW() WHERE `id` = 910305;
