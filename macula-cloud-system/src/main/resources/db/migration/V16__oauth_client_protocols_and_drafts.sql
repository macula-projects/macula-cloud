-- 不推断历史协议和启用状态；仅显式完整配置才能启用。
ALTER TABLE sys_oauth2_client
    ADD COLUMN enabled_protocols VARCHAR(64) NULL COMMENT '明确启用的协议集合',
    ADD COLUMN default_protocol VARCHAR(32) NULL COMMENT '生成接入入口的默认协议',
    ADD COLUMN configuration_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ENABLED',
    MODIFY COLUMN tenant_id BIGINT NULL COMMENT '认证租户，草稿可空，启用必填',
    MODIFY COLUMN user_type VARCHAR(25) COLLATE utf8mb4_unicode_ci NULL COMMENT '用户目录，草稿可空，启用必填';
