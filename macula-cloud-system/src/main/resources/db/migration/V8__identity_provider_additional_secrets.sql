-- 身份源附加密钥使用独立加密 JSON 字段，主密钥继续保留在 app_secret。
ALTER TABLE sys_identity_provider
    ADD COLUMN secret_config_json TEXT DEFAULT NULL
        COMMENT '经 CryptoField 加密的附加提供商凭据' AFTER config_json;
