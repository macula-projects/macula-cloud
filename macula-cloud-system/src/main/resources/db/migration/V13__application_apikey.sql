-- 撤销记录保留用于重建清理，凭据按大小写区分。
CREATE TABLE sys_application_apikey_tenant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    api_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    remark VARCHAR(255) NULL,
    access_path VARCHAR(2000) NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by VARCHAR(50) NOT NULL DEFAULT '*SYSADM',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_by VARCHAR(50) NOT NULL DEFAULT '*SYSADM',
    last_update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_application_apikey (api_key),
    KEY idx_application_apikey_owner (tenant_id, application_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用API Key';

-- 复用应用管理菜单授权，不硬编码菜单ID，不额外授权普通用户。
INSERT INTO sys_permission_tenant
    (name, menu_id, url_perm, tenant_id, create_time, last_update_time)
SELECT CONCAT('API Key ', methods.method), m.id,
       CONCAT(methods.method, ':/system/api/v1/app/*/api-keys/**'), m.tenant_id, NOW(), NOW()
FROM sys_menu_tenant m
CROSS JOIN (
    SELECT 'GET' AS method UNION ALL SELECT 'POST' UNION ALL SELECT 'PUT' UNION ALL SELECT 'DELETE'
) methods
WHERE m.path = '/system/application'
AND m.id = (SELECT MIN(selected.id) FROM sys_menu_tenant selected WHERE selected.path = '/system/application')
AND NOT EXISTS (
    SELECT 1 FROM sys_permission_tenant p
    WHERE p.url_perm = CONCAT(methods.method, ':/system/api/v1/app/*/api-keys/**')
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT DISTINCT rm.role_id, p.id
FROM sys_permission_tenant p
JOIN sys_menu_tenant m ON m.id = p.menu_id
JOIN sys_role_menu rm ON rm.menu_id = m.id
JOIN sys_role_tenant r ON r.id = rm.role_id AND r.tenant_id = m.tenant_id
WHERE m.path = '/system/application'
AND p.url_perm LIKE '%:/system/api/v1/app/*/api-keys/**'
AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = rm.role_id AND rp.permission_id = p.id);
