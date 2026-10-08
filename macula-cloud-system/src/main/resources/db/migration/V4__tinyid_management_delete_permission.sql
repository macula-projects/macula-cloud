SET @tinyid_menu_id = (
    SELECT id
    FROM sys_menu_tenant
    WHERE tenant_id = 1
      AND path = '/tinyid/management'
    ORDER BY id
    LIMIT 1
);

INSERT INTO sys_permission_tenant
    (name, menu_id, url_perm, tenant_id, create_by, create_time, last_update_by, last_update_time)
SELECT 'TinyID管理删除', @tinyid_menu_id, 'DELETE:/tinyid/api/v1/admin/**', 1,
       '*SYSADM', NOW(), '*SYSADM', NOW()
WHERE @tinyid_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission_tenant
      WHERE tenant_id = 1
        AND url_perm = 'DELETE:/tinyid/api/v1/admin/**'
  );

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_permission_tenant
WHERE tenant_id = 1
  AND url_perm = 'DELETE:/tinyid/api/v1/admin/**';
