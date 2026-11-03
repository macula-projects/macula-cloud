-- 归属租户仍为 tenant_id；认证租户不改写历史数据，NULL 表示沿用归属租户。
ALTER TABLE sys_application_tenant
    ADD COLUMN auth_tenant_id BIGINT NULL COMMENT '应用认证后的业务租户，空值使用归属租户' AFTER tenant_id;
