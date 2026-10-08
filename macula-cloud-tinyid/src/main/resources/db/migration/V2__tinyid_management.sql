CREATE UNIQUE INDEX uk_tiny_id_token_token_biz_type
    ON tiny_id_token (token, biz_type);
