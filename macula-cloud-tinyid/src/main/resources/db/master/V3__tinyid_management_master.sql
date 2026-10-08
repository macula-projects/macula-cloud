CREATE TABLE tiny_id_audit_log
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

CREATE TABLE tiny_id_management_request
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    data_source_key  VARCHAR(64)   NOT NULL COMMENT '数据源键，GLOBAL表示全局操作',
    idempotency_key  VARCHAR(64)   NOT NULL COMMENT '幂等键',
    operation_type   VARCHAR(64)   NOT NULL COMMENT '操作类型',
    resource_type    VARCHAR(64)   NOT NULL COMMENT '资源类型',
    resource_key     VARCHAR(255)           DEFAULT NULL COMMENT '资源标识，不保存原始Token',
    execution_status VARCHAR(32)   NOT NULL COMMENT '执行状态',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tiny_id_management_request (data_source_key, idempotency_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'TinyID管理幂等请求';
