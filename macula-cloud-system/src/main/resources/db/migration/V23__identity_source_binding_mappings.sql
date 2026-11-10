-- 集合列NULL保持旧单条合同，不回填凭据、版本或已有身份绑定。
ALTER TABLE sys_identity_provider ADD COLUMN binding_mappings_json TEXT NULL COMMENT '首次自动绑定字段映射集合';
