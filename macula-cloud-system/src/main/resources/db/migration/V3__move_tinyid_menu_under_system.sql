UPDATE sys_menu_tenant AS tinyid_menu
    INNER JOIN sys_menu_tenant AS system_menu
        ON system_menu.tenant_id = tinyid_menu.tenant_id
            AND system_menu.path = '/system'
SET tinyid_menu.parent_id = system_menu.id,
    tinyid_menu.name = 'ID管理',
    tinyid_menu.last_update_by = '*SYSADM',
    tinyid_menu.last_update_time = NOW()
WHERE tinyid_menu.tenant_id = 1
  AND tinyid_menu.path = '/tinyid/management';
