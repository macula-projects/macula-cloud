CREATE TABLE tiny_id_datasource_order
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    data_source_key VARCHAR(64) NOT NULL COMMENT '稳定的数据源键',
    sequence_no     INT         NOT NULL COMMENT '只增不复用的实例顺序',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tiny_id_datasource_order_key (data_source_key),
    UNIQUE KEY uk_tiny_id_datasource_order_sequence (sequence_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'TinyID数据源稳定顺序';
