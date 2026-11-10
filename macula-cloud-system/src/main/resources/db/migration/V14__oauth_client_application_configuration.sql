-- 不推断、不回填历史应用，不改变现有凭据、grant及身份绑定。
ALTER TABLE sys_oauth2_client
    ADD COLUMN application_type VARCHAR(32) NULL COMMENT '应用架构类型，历史记录待确认',
    ADD COLUMN miniapp_platform VARCHAR(32) NULL COMMENT '小程序平台',
    ADD COLUMN hosted_login_methods VARCHAR(64) NULL COMMENT '托管页本地方式，NULL继承，空字符串全部关闭';
