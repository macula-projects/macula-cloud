INSERT INTO sys_menu_tenant
    (tenant_id, parent_id, type, name, path, component, perm, icon, sort, visible, redirect,
     create_by, create_time, last_update_by, last_update_time, full_page)
SELECT 1, 0, 1, 'TinyID管理', '/tinyid/management', 'tinyid/management/index', '', 'el-icon-key', 20, 1, '',
       '*SYSADM', NOW(), '*SYSADM', NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu_tenant WHERE tenant_id = 1 AND path = '/tinyid/management'
);

SET @tinyid_menu_id = (
    SELECT id FROM sys_menu_tenant WHERE tenant_id = 1 AND path = '/tinyid/management' ORDER BY id LIMIT 1
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, @tinyid_menu_id);

INSERT INTO sys_permission_tenant
    (name, menu_id, url_perm, tenant_id, create_by, create_time, last_update_by, last_update_time)
SELECT 'TinyID管理查询', @tinyid_menu_id, 'GET:/tinyid/api/v1/admin/**', 1, '*SYSADM', NOW(), '*SYSADM', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission_tenant WHERE url_perm = 'GET:/tinyid/api/v1/admin/**'
);

INSERT INTO sys_permission_tenant
    (name, menu_id, url_perm, tenant_id, create_by, create_time, last_update_by, last_update_time)
SELECT 'TinyID管理新增', @tinyid_menu_id, 'POST:/tinyid/api/v1/admin/**', 1, '*SYSADM', NOW(), '*SYSADM', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission_tenant WHERE url_perm = 'POST:/tinyid/api/v1/admin/**'
);

INSERT INTO sys_permission_tenant
    (name, menu_id, url_perm, tenant_id, create_by, create_time, last_update_by, last_update_time)
SELECT 'TinyID管理修改', @tinyid_menu_id, 'PUT:/tinyid/api/v1/admin/**', 1, '*SYSADM', NOW(), '*SYSADM', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission_tenant WHERE url_perm = 'PUT:/tinyid/api/v1/admin/**'
);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_permission_tenant
WHERE url_perm IN (
    'GET:/tinyid/api/v1/admin/**',
    'POST:/tinyid/api/v1/admin/**',
    'PUT:/tinyid/api/v1/admin/**'
);
