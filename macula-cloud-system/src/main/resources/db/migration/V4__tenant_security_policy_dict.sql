-- 租户安全策略改为 sys_dict_item_tenant 中逐 name/value 存储。
-- 唯一索引创建前以 CHECK guard 明确阻止任何历史重复项被静默合并。
CREATE TEMPORARY TABLE security_policy_duplicate_guard (
  duplicate_count bigint NOT NULL,
  CONSTRAINT ck_security_policy_no_duplicate CHECK (duplicate_count = 0)
);

INSERT INTO security_policy_duplicate_guard (duplicate_count)
SELECT COUNT(*)
FROM (
  SELECT tenant_id, type_code, name
  FROM sys_dict_item_tenant
  GROUP BY tenant_id, type_code, name
  HAVING COUNT(*) > 1
) duplicated_items;

DROP TEMPORARY TABLE security_policy_duplicate_guard;

ALTER TABLE sys_dict_item_tenant
  MODIFY COLUMN value text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典项值',
  ADD UNIQUE KEY ux_dict_item_tenant_type_name (tenant_id, type_code, name);

-- 继承曾拥有任一旧安全菜单或权限的角色，随后安全删除旧菜单和权限关系。
CREATE TEMPORARY TABLE security_policy_role_guard (
  role_id bigint NOT NULL,
  PRIMARY KEY (role_id)
);

INSERT IGNORE INTO security_policy_role_guard (role_id)
SELECT id FROM sys_role_tenant WHERE tenant_id = 1 AND code = 'ROOT';

INSERT IGNORE INTO security_policy_role_guard (role_id)
SELECT rm.role_id
FROM sys_role_menu rm
JOIN sys_menu_tenant m ON m.id = rm.menu_id
JOIN sys_role_tenant r ON r.id = rm.role_id AND r.tenant_id = m.tenant_id
WHERE m.tenant_id = 1
  AND m.path IN ('/system/security-settings', '/system/password-policy');

INSERT IGNORE INTO security_policy_role_guard (role_id)
SELECT rp.role_id
FROM sys_role_permission rp
JOIN sys_permission_tenant p ON p.id = rp.permission_id
JOIN sys_role_tenant r ON r.id = rp.role_id AND r.tenant_id = p.tenant_id
WHERE p.tenant_id = 1
  AND p.url_perm IN (
  'GET:/system/api/v1/security-settings/**',
  'PUT:/system/api/v1/security-settings/**',
  'GET:/system/api/v1/password-policy/**',
  'PUT:/system/api/v1/password-policy/**'
);

DELETE rp
FROM sys_role_permission rp
JOIN sys_permission_tenant p ON p.id = rp.permission_id
WHERE p.menu_id IN (
  SELECT id FROM sys_menu_tenant
  WHERE tenant_id = 1
    AND path IN ('/system/security-settings', '/system/password-policy')
);

DELETE FROM sys_permission_tenant
WHERE menu_id IN (
  SELECT id FROM sys_menu_tenant
  WHERE tenant_id = 1
    AND path IN ('/system/security-settings', '/system/password-policy')
);

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu_tenant m ON m.id = rm.menu_id
WHERE m.tenant_id = 1
  AND m.path IN ('/system/security-settings', '/system/password-policy');

DELETE FROM sys_menu_tenant
WHERE tenant_id = 1
  AND path IN ('/system/security-settings', '/system/password-policy');

INSERT INTO sys_menu_tenant
  (tenant_id, parent_id, type, name, path, component, perm, icon, sort, visible, redirect,
   create_by, create_time, last_update_by, last_update_time, full_page)
SELECT 1, 1, 1, '安全策略', '/system/security-policy', 'system/securityPolicy/index', '',
       'el-icon-lock', 11, 1, '', '*SYSADM', NOW(), '*SYSADM', NOW(), 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu_tenant WHERE tenant_id = 1 AND path = '/system/security-policy'
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT roles.role_id, menu.id
FROM security_policy_role_guard roles
JOIN sys_menu_tenant menu ON menu.tenant_id = 1 AND menu.path = '/system/security-policy';

INSERT INTO sys_permission_tenant
  (name, menu_id, url_perm, tenant_id, create_by, create_time, last_update_by, last_update_time)
SELECT permission_name, menu.id, url_perm, 1, '*SYSADM', NOW(), '*SYSADM', NOW()
FROM sys_menu_tenant menu
JOIN (
  SELECT '安全策略读取' permission_name, 'GET:/system/api/v1/security-policy/**' url_perm
  UNION ALL SELECT '通用安全保存', 'PUT:/system/api/v1/security-policy/basic'
  UNION ALL SELECT '防御策略保存', 'PUT:/system/api/v1/security-policy/defense'
  UNION ALL SELECT '密码策略保存', 'PUT:/system/api/v1/security-policy/password'
) permissions
WHERE menu.tenant_id = 1 AND menu.path = '/system/security-policy'
  AND NOT EXISTS (
    SELECT 1 FROM sys_permission_tenant existing WHERE existing.url_perm = permissions.url_perm
  );

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT roles.role_id, permission.id
FROM security_policy_role_guard roles
JOIN sys_permission_tenant permission ON permission.tenant_id = 1
WHERE permission.url_perm IN (
  'GET:/system/api/v1/security-policy/**',
  'PUT:/system/api/v1/security-policy/basic',
  'PUT:/system/api/v1/security-policy/defense',
  'PUT:/system/api/v1/security-policy/password'
);

DROP TEMPORARY TABLE security_policy_role_guard;
