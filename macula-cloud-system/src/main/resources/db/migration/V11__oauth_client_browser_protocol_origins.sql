-- 将既有 allowed_origins 扩展为浏览器协议端点的统一精确 Origin allowlist。
-- 本迁移只更新列注释，不回填、推导或修改任何 Client 的 Origin 数据。
ALTER TABLE sys_oauth2_client
    MODIFY COLUMN allowed_origins VARCHAR(2048) NOT NULL DEFAULT ''
        COMMENT '浏览器协议端点允许的精确 Origin，逗号分隔';
