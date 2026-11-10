-- 来源运行合同与通讯录策略独立于厂商凭据；不猜填组织或flow。
ALTER TABLE sys_identity_provider
    ADD COLUMN identity_flow VARCHAR(64) NULL,
    ADD COLUMN directory_policy VARCHAR(32) NOT NULL DEFAULT 'IDENTITY_ONLY',
    ADD COLUMN organization_id VARCHAR(255) NULL,
    ADD COLUMN oauth_redirect_uri VARCHAR(2048) NULL;
