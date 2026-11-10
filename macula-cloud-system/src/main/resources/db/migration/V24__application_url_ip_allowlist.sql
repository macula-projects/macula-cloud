-- 追加可空列，历史应用及 API Key 保持原有不限 IP 行为；不修改旧迁移。
ALTER TABLE sys_application_tenant
    MODIFY COLUMN access_path VARCHAR(2000) NULL COMMENT '可访问路径，每行一条，兼容旧逗号分隔',
    ADD COLUMN allow_ips VARCHAR(2000) NULL COMMENT '允许来源IP/CIDR，每行一条，空表示不限';

ALTER TABLE sys_application_apikey_tenant
    ADD COLUMN allow_ips VARCHAR(2000) NULL COMMENT '允许来源IP/CIDR，每行一条，空继承应用';
