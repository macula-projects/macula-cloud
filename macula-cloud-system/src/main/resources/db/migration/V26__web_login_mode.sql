-- 登录入口独立于应用架构；只加列，不转换历史IN_APP或重写凭据/绑定。
ALTER TABLE sys_oauth2_client ADD COLUMN web_login_mode VARCHAR(16) NULL COMMENT 'Web/SPA登录模式：BROWSER或IN_APP';
