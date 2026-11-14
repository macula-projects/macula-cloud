-- 只增加空集合策略，不猜填历史资源服务器许可，不修改已有凭据或绑定。
ALTER TABLE sys_iam_application
    ADD COLUMN allowed_user_types VARCHAR(128) NOT NULL DEFAULT '[]' COMMENT '资源服务器允许的用户类型JSON数组';
