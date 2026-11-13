-- 旧的“厂商类型 + 用户名”绑定已退役；升级前必须归档存量记录。
-- 当前绑定保存在 sys_identity_provider_user_binding，不能由旧身份猜测来源或自动合并。
-- MySQL DDL不可事务回滚；恢复旧记录须使用升级前备份。
DROP TABLE IF EXISTS sys_user_identity_binding;
