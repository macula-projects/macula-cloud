-- OAuth Client 浏览器第三方登录策略。旧 Client 默认只允许本地认证。
ALTER TABLE sys_oauth2_client
    ADD COLUMN browser_login_provider_types VARCHAR(255) NOT NULL DEFAULT ''
        COMMENT '允许的浏览器第三方登录提供商，逗号分隔' AFTER allowed_origins,
    ADD COLUMN direct_login_provider_type VARCHAR(32) NULL
        COMMENT '进入登录页时直接发起的第三方登录提供商' AFTER browser_login_provider_types;

-- 企微端内网页改由 /authorize 驱动浏览器认证，不再作为 external_code 类型。
DELETE FROM sys_oauth2_client_external_provider
WHERE code_type = 'WECOM_WEBVIEW_CODE';

UPDATE sys_oauth2_client
SET external_code_types = TRIM(BOTH ',' FROM REPLACE(
    CONCAT(',', external_code_types, ','), ',WECOM_WEBVIEW_CODE,', ','))
WHERE FIND_IN_SET('WECOM_WEBVIEW_CODE', external_code_types) > 0;
