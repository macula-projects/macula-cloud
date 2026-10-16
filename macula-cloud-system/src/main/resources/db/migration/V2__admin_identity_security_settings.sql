-- 平台级身份、安全与消息配置。V1 已执行的数据库仅增量升级，不清理现有数据。
CREATE TABLE sys_identity_provider (
  id bigint NOT NULL AUTO_INCREMENT,
  name varchar(100) NOT NULL,
  provider_type varchar(32) NOT NULL,
  app_id varchar(255) DEFAULT NULL,
  app_secret varchar(2048) DEFAULT NULL,
  callback_url varchar(1024) DEFAULT NULL,
  config_json text,
  enabled tinyint(1) NOT NULL DEFAULT 0,
  visible tinyint(1) NOT NULL DEFAULT 0,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY ux_identity_provider_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台身份提供商配置';

CREATE TABLE sys_security_settings (
  id bigint NOT NULL,
  settings_json text NOT NULL,
  last_update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台通用安全设置';

CREATE TABLE sys_password_policy (
  id bigint NOT NULL,
  settings_json text NOT NULL,
  last_update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台密码策略配置';

CREATE TABLE sys_message_provider (
  id bigint NOT NULL AUTO_INCREMENT,
  channel varchar(8) NOT NULL,
  provider_type varchar(32) NOT NULL,
  name varchar(100) NOT NULL,
  endpoint varchar(1024) DEFAULT NULL,
  account varchar(255) DEFAULT NULL,
  credential varchar(2048) DEFAULT NULL,
  region_code varchar(64) DEFAULT NULL,
  sender varchar(255) DEFAULT NULL,
  config_json text,
  enabled tinyint(1) NOT NULL DEFAULT 0,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY ux_message_channel_name (channel, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台邮件短信服务配置';

CREATE TABLE sys_message_template (
  id bigint NOT NULL AUTO_INCREMENT,
  channel varchar(8) NOT NULL,
  template_key varchar(64) NOT NULL,
  provider_type varchar(32) NOT NULL DEFAULT '',
  language_code varchar(16) NOT NULL DEFAULT 'zh-CN',
  title varchar(255) DEFAULT NULL,
  content text NOT NULL,
  provider_template_code varchar(128) DEFAULT NULL,
  enabled tinyint(1) NOT NULL DEFAULT 1,
  last_update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY ux_message_template (channel, template_key, provider_type, language_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台消息模板覆盖配置';

ALTER TABLE sys_oauth2_client MODIFY COLUMN client_secret varchar(200) NULL COMMENT '客户端密钥摘要，公共客户端为空';

-- 动态路由仍沿用租户 1 的系统管理目录，但数据和 API 均为平台级且仅 ROOT 可访问。
INSERT INTO sys_menu_tenant
  (tenant_id, parent_id, type, name, path, component, perm, icon, sort, visible, redirect,
   create_by, create_time, last_update_by, last_update_time, full_page)
SELECT 1, 1, 1, x.name, x.path, x.component, '', 'el-icon-setting', x.sort, 1, '',
       '*SYSADM', NOW(), '*SYSADM', NOW(), 0
FROM (
  SELECT '身份提供商' name, '/system/identity-provider' path, 'system/identityProvider/index' component, 10 sort
  UNION ALL SELECT '通用安全', '/system/security-settings', 'system/securitySettings/index', 11
  UNION ALL SELECT '密码策略', '/system/password-policy', 'system/passwordPolicy/index', 12
  UNION ALL SELECT '消息设置', '/system/message-settings', 'system/messageSettings/index', 13
  UNION ALL SELECT 'IAM接入应用', '/system/oauth2-client', 'system/oauth2Client/index', 14
) x
WHERE NOT EXISTS (SELECT 1 FROM sys_menu_tenant m WHERE m.tenant_id = 1 AND m.path = x.path);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role_tenant r JOIN sys_menu_tenant m ON m.tenant_id = 1
WHERE r.code = 'ROOT' AND r.tenant_id = 1 AND m.path IN
  ('/system/identity-provider', '/system/security-settings', '/system/password-policy',
   '/system/message-settings', '/system/oauth2-client')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_permission_tenant
  (name, menu_id, url_perm, tenant_id, create_by, create_time, last_update_by, last_update_time)
SELECT CONCAT(m.name, '管理'), m.id, CONCAT(v.verb, ':/system/api/v1/', v.resource, '/**'), 1,
       '*SYSADM', NOW(), '*SYSADM', NOW()
FROM sys_menu_tenant m
JOIN (
  SELECT 'GET' verb, 'identity-providers' resource, '/system/identity-provider' path
  UNION ALL SELECT 'POST', 'identity-providers', '/system/identity-provider'
  UNION ALL SELECT 'PUT', 'identity-providers', '/system/identity-provider'
  UNION ALL SELECT 'DELETE', 'identity-providers', '/system/identity-provider'
  UNION ALL SELECT 'GET', 'security-settings', '/system/security-settings'
  UNION ALL SELECT 'PUT', 'security-settings', '/system/security-settings'
  UNION ALL SELECT 'GET', 'password-policy', '/system/password-policy'
  UNION ALL SELECT 'PUT', 'password-policy', '/system/password-policy'
  UNION ALL SELECT 'GET', 'message-settings', '/system/message-settings'
  UNION ALL SELECT 'POST', 'message-settings', '/system/message-settings'
  UNION ALL SELECT 'PUT', 'message-settings', '/system/message-settings'
  UNION ALL SELECT 'DELETE', 'message-settings', '/system/message-settings'
  UNION ALL SELECT 'GET', 'oauth2-clients', '/system/oauth2-client'
  UNION ALL SELECT 'POST', 'oauth2-clients', '/system/oauth2-client'
  UNION ALL SELECT 'PUT', 'oauth2-clients', '/system/oauth2-client'
  UNION ALL SELECT 'DELETE', 'oauth2-clients', '/system/oauth2-client'
) v ON m.tenant_id = 1 AND m.path = v.path
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission_tenant p
  WHERE p.url_perm = CONCAT(v.verb, ':/system/api/v1/', v.resource, '/**')
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role_tenant r JOIN sys_permission_tenant p ON p.tenant_id = 1
WHERE r.code = 'ROOT' AND r.tenant_id = 1 AND p.menu_id IN (
  SELECT m.id FROM sys_menu_tenant m WHERE m.tenant_id = 1 AND m.path IN
    ('/system/identity-provider', '/system/security-settings', '/system/password-policy',
     '/system/message-settings', '/system/oauth2-client')
)
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
