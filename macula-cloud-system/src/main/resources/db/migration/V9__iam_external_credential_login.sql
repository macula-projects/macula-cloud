-- 空白不是登录标识，可以安全归一化为 NULL；任何非空业务值都不在迁移中改写。
UPDATE sys_user SET mobile = NULL WHERE mobile IS NOT NULL AND TRIM(mobile) = '';
UPDATE sys_user SET email = NULL WHERE email IS NOT NULL AND TRIM(email) = '';

DELIMITER //
DROP PROCEDURE IF EXISTS validate_external_credential_user_identifiers//
CREATE PROCEDURE validate_external_credential_user_identifiers()
BEGIN
    IF EXISTS (
        SELECT 1 FROM sys_user
        WHERE mobile IS NOT NULL
          AND (CAST(mobile AS BINARY) <>
            CAST(REPLACE(REPLACE(TRIM(mobile), ' ', ''), '-', '') AS BINARY)
            OR mobile NOT REGEXP '^[+]?[0-9]{6,20}$')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'sys_user contains non-canonical mobile values; run the documented read-only diagnostic';
    END IF;

    IF EXISTS (
        SELECT 1 FROM sys_user
        WHERE email IS NOT NULL
          AND (CAST(email AS BINARY) <> CAST(LOWER(TRIM(email)) AS BINARY)
            OR CHAR_LENGTH(email) > 254
            OR email NOT REGEXP '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'sys_user contains non-canonical email values; run the documented read-only diagnostic';
    END IF;

    IF EXISTS (
        SELECT mobile FROM sys_user WHERE mobile IS NOT NULL
        GROUP BY mobile HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'sys_user contains duplicate mobile values; run the documented read-only diagnostic';
    END IF;

    IF EXISTS (
        SELECT email FROM sys_user WHERE email IS NOT NULL
        GROUP BY email HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'sys_user contains duplicate email values; run the documented read-only diagnostic';
    END IF;
END//
DELIMITER ;

CALL validate_external_credential_user_identifiers();
DROP PROCEDURE validate_external_credential_user_identifiers;

DELIMITER //
DROP PROCEDURE IF EXISTS ensure_external_credential_user_indexes//
CREATE PROCEDURE ensure_external_credential_user_indexes()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND index_name = 'uk_sys_user_mobile'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND index_name = 'uk_sys_user_mobile'
        GROUP BY index_name
        HAVING COUNT(*) = 1 AND MAX(non_unique) = 0 AND MAX(column_name) = 'mobile'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'uk_sys_user_mobile exists with an incompatible definition';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND index_name = 'uk_sys_user_mobile'
    ) THEN
        ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_mobile (mobile);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND index_name = 'uk_sys_user_email'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND index_name = 'uk_sys_user_email'
        GROUP BY index_name
        HAVING COUNT(*) = 1 AND MAX(non_unique) = 0 AND MAX(column_name) = 'email'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'uk_sys_user_email exists with an incompatible definition';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND index_name = 'uk_sys_user_email'
    ) THEN
        ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_email (email);
    END IF;
END//
DELIMITER ;

CALL ensure_external_credential_user_indexes();
DROP PROCEDURE ensure_external_credential_user_indexes;

-- OAuth Client 外部凭证策略；旧 Client 默认不允许任何 external_code。
ALTER TABLE sys_oauth2_client
    ADD COLUMN external_code_types VARCHAR(1024) NOT NULL DEFAULT ''
        COMMENT '允许的外部凭证类型，逗号分隔' AFTER scopes,
    ADD COLUMN allowed_origins VARCHAR(2048) NOT NULL DEFAULT ''
        COMMENT '验证码协议端点允许的精确 Origin，逗号分隔' AFTER external_code_types;

CREATE TABLE sys_oauth2_client_external_provider (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    client_id VARCHAR(100) NOT NULL COMMENT 'OAuth Client ID',
    code_type VARCHAR(64) NOT NULL COMMENT 'ExternalCodeType 稳定值',
    identity_provider_id BIGINT NOT NULL COMMENT '固定身份源配置 ID',
    create_by VARCHAR(50) NOT NULL DEFAULT '*SYSADM' COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_update_by VARCHAR(50) NOT NULL DEFAULT '*SYSADM' COMMENT '最后修改人',
    last_update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth2_client_external_code_type (client_id, code_type),
    KEY idx_oauth2_client_external_provider (identity_provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='OAuth Client 外部凭证类型与身份源配置映射';

-- 只读诊断示例（按需由受权运维人员执行，不进入普通迁移日志）：
-- SELECT id, 'NON_CANONICAL_MOBILE' AS issue FROM sys_user
--  WHERE mobile IS NOT NULL
--    AND (CAST(mobile AS BINARY) <>
--      CAST(REPLACE(REPLACE(TRIM(mobile), ' ', ''), '-', '') AS BINARY)
--      OR mobile NOT REGEXP '^[+]?[0-9]{6,20}$');
-- SELECT id, 'NON_CANONICAL_EMAIL' AS issue FROM sys_user
--  WHERE email IS NOT NULL
--    AND (CAST(email AS BINARY) <> CAST(LOWER(TRIM(email)) AS BINARY)
--      OR CHAR_LENGTH(email) > 254
--      OR email NOT REGEXP '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$');
-- SELECT id, 'DUPLICATE_MOBILE' AS issue FROM sys_user
--  WHERE mobile IN (SELECT mobile FROM sys_user WHERE mobile IS NOT NULL GROUP BY mobile HAVING COUNT(*) > 1);
-- SELECT id, 'DUPLICATE_EMAIL' AS issue FROM sys_user
--  WHERE email IN (SELECT email FROM sys_user WHERE email IS NOT NULL GROUP BY email HAVING COUNT(*) > 1);
