-- 身份源结构化 JSON 配置：V2 已部署，采用增量扩列，不覆盖已有配置行。
ALTER TABLE sys_identity_provider
  ADD COLUMN remark varchar(500) DEFAULT NULL COMMENT '管理员备注' AFTER name,
  MODIFY COLUMN app_secret text DEFAULT NULL COMMENT '经 CryptoField 加密的提供商凭据';
