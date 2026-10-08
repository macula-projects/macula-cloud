CREATE TABLE IF NOT EXISTS tiny_id_audit_log
(
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    operator_name  VARCHAR(64)            DEFAULT NULL COMMENT '操作人',
    action_name    VARCHAR(255)   NOT NULL COMMENT '操作名称',
    request_method VARCHAR(16)    NOT NULL COMMENT '请求方法',
    request_uri    VARCHAR(255)   NOT NULL COMMENT '请求路径',
    result_status  TINYINT        NOT NULL COMMENT '0失败 1成功',
    error_summary  VARCHAR(1000)           DEFAULT NULL COMMENT '脱敏错误摘要',
    client_ip      VARCHAR(64)             DEFAULT NULL COMMENT '客户端地址',
    create_time    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_tiny_id_audit_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'TinyID管理审计日志';
