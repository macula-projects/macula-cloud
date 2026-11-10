-- 统一新模型：不推断旧协议/来源、不迁移旧账号绑定，旧配置需重新配置。
ALTER TABLE sys_oauth2_client
    ADD COLUMN protocol_mode VARCHAR(32) NULL COMMENT 'OIDC或OAUTH2_1，缺失不可启用',
    ADD COLUMN browser_provider_bindings TEXT NULL COMMENT '厂商到具体身份源ID的固定关联';
ALTER TABLE sys_identity_provider
    DROP INDEX uk_identity_provider_enabled_type,
    DROP COLUMN enabled_provider_type,
    ADD COLUMN source_type VARCHAR(64) NULL COMMENT '具体来源类型，缺失不可认证',
    ADD COLUMN auto_bind_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '首次登录自动绑定开关',
    ADD COLUMN binding_rule VARCHAR(64) NULL COMMENT '受控核验属性匹配规则',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '配置并发版本';
CREATE TABLE sys_registration_identity_binding (
    id BIGINT NOT NULL AUTO_INCREMENT,
    registration_id BIGINT NOT NULL,
    identity_kind VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    identity_value VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    user_type VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    create_by VARCHAR(50) NOT NULL DEFAULT '*SYSADM',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_by VARCHAR(50) NOT NULL DEFAULT '*SYSADM',
    last_update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_registration_identity (registration_id, identity_kind, identity_value),
    UNIQUE KEY uk_registration_user (registration_id, identity_kind, user_type, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='具体身份源到稳定用户ID的绑定';
