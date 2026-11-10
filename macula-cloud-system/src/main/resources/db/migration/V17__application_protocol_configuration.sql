-- 独立协议配置；不修改V16，不复制应用Secret。
CREATE TABLE sys_application_protocol_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    protocol VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
    schema_version INT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    configuration JSON NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) NULL,
    last_update_by VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_application_protocol (application_id, protocol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
