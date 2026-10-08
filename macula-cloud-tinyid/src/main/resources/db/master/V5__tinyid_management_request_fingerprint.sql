ALTER TABLE tiny_id_management_request
    ADD COLUMN request_fingerprint CHAR(64) DEFAULT NULL COMMENT '规范化请求SHA-256指纹' AFTER resource_key;
