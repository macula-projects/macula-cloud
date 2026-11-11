-- 一次性为已允许托管登录的合法显式集合增加EMAIL；NULL继承默认，空值全关。
-- 不在读取时补值，管理员之后取消EMAIL不会在再次migrate时被打开。
-- 只改托管认证方式，不改authorization_version、凭据、协议、身份源或绑定。
UPDATE sys_oauth2_client
SET hosted_login_methods = CONCAT(hosted_login_methods, ',EMAIL')
WHERE purpose = 'APPLICATION'
  AND application_type IN ('WEB_SERVER', 'SPA', 'NATIVE', 'MINIAPP')
  AND hosted_login_methods IS NOT NULL
  AND REGEXP_LIKE(hosted_login_methods, '^(PASSWORD|SMS)(,(PASSWORD|SMS))*$', 'c');
