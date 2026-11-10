-- 只扩展来源映射；未知旧规则保留待管理员明确处理。
ALTER TABLE sys_identity_provider
    ADD COLUMN binding_source_field VARCHAR(32) NULL,
    ADD COLUMN binding_target_field VARCHAR(32) NULL;
UPDATE sys_identity_provider
SET binding_source_field = CASE binding_rule
        WHEN 'MEMBER_ID_TO_USERNAME' THEN 'member_id'
        WHEN 'EMPLOYEE_NO_TO_USERNAME' THEN 'employee_no'
        WHEN 'VERIFIED_MOBILE_TO_MOBILE' THEN 'mobile' END,
    binding_target_field = CASE binding_rule
        WHEN 'MEMBER_ID_TO_USERNAME' THEN 'username'
        WHEN 'EMPLOYEE_NO_TO_USERNAME' THEN 'username'
        WHEN 'VERIFIED_MOBILE_TO_MOBILE' THEN 'mobile' END
WHERE binding_rule IN ('MEMBER_ID_TO_USERNAME', 'EMPLOYEE_NO_TO_USERNAME', 'VERIFIED_MOBILE_TO_MOBILE');
