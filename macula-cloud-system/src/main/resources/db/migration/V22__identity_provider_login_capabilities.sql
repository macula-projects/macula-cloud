-- 具体注册保持不变，只等价登记原能力；不合并注册或改写绑定和密文。
ALTER TABLE sys_identity_provider
    ADD COLUMN credential_type VARCHAR(64) NULL COMMENT '真实应用注册类型',
    ADD COLUMN capabilities_json JSON NULL COMMENT '已启用能力到固定兑换合同';
ALTER TABLE sys_oauth2_client
    ADD COLUMN browser_capability_bindings TEXT NULL COMMENT '网页能力到固定来源ID';
UPDATE sys_identity_provider
SET credential_type = CASE
    WHEN source_type LIKE 'WECOM_%' THEN 'WECOM_APPLICATION'
    WHEN source_type LIKE 'FEISHU_%' THEN 'FEISHU_APPLICATION'
    WHEN source_type LIKE 'DINGTALK_%' THEN 'DINGTALK_APPLICATION'
    ELSE source_type END,
    capabilities_json = CASE source_type
    WHEN 'WECOM_WEB' THEN JSON_OBJECT('WECOM_BROWSER', COALESCE(identity_flow, ''), 'WECOM_WEBVIEW', COALESCE(identity_flow, ''))
    WHEN 'WECHAT_MINIAPP' THEN JSON_OBJECT('WECHAT_MINIAPP_LOGIN', COALESCE(identity_flow, ''), 'WECHAT_MINIAPP_PHONE', COALESCE(identity_flow, ''))
    WHEN 'ALIPAY_APPLICATION' THEN JSON_OBJECT('ALIPAY_CONFIGURATION', '')
    ELSE JSON_OBJECT(source_type, COALESCE(identity_flow, '')) END
WHERE source_type IS NOT NULL;
