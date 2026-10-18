-- Apply the historical-data precondition first so a duplicate-provider failure leaves no later objects behind.
ALTER TABLE sys_identity_provider
    ADD COLUMN enabled_provider_type VARCHAR(32)
        GENERATED ALWAYS AS (CASE WHEN enabled = 1 THEN provider_type ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_identity_provider_enabled_type (enabled_provider_type);

-- IAM third-party account binding.
CREATE TABLE sys_user_identity_binding (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    provider_type VARCHAR(32) NOT NULL COMMENT '身份提供商类型',
    provider_subject VARCHAR(255) NOT NULL COMMENT '第三方稳定身份标识',
    username VARCHAR(64) NOT NULL COMMENT '平台用户名',
    create_by VARCHAR(50) NOT NULL DEFAULT '*SYSADM' COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_update_by VARCHAR(50) NOT NULL DEFAULT '*SYSADM' COMMENT '最后修改人',
    last_update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_identity_subject (provider_type, provider_subject),
    UNIQUE KEY uk_user_identity_username (provider_type, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方身份与平台用户名绑定';
