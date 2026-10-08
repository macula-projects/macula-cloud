/*
 * Copyright (c) 2023 Macula
 *   macula.dev, China
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.macula.cloud.tinyid.listener;

import dev.macula.boot.starter.auditlog.event.OperLogEvent;
import dev.macula.cloud.tinyid.config.DynamicDataSource;
import dev.macula.cloud.tinyid.mapper.TinyIdAuditLogMapper;
import dev.macula.cloud.tinyid.pojo.entity.TinyIdAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Persists sanitized TinyID management audit events in physical data source sequence {@code 0}.
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class TinyIdAuditLogEventListener {

    /** 日志记录器。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(TinyIdAuditLogEventListener.class);
    /** 管理审计日志 Mapper。 */
    private final TinyIdAuditLogMapper auditLogMapper;
    /** 发号随机路由与管理显式路由共用的数据源。 */
    private final DynamicDataSource routingDataSource;

    /**
     * 创建 TinyID 审计事件监听器。
     *
     * @param auditLogMapper 管理审计日志 Mapper
     * @param routingDataSource 动态路由数据源
     */
    public TinyIdAuditLogEventListener(TinyIdAuditLogMapper auditLogMapper, DynamicDataSource routingDataSource) {
        this.auditLogMapper = auditLogMapper;
        this.routingDataSource = routingDataSource;
    }

    /**
     * 异步保存 TinyID 管理接口产生的标准操作日志事件。
     *
     * @param event Macula AuditLog Starter 发布的操作日志事件
     */
    @Async
    @EventListener
    public void save(OperLogEvent event) {
        if (event.getOperUrl() == null || !event.getOperUrl().contains("/api/v1/admin")) {
            return;
        }
        try {
            routingDataSource.execute(0, () -> auditLogMapper.insert(toAuditLog(event)));
        } catch (RuntimeException ex) {
            LOGGER.error("Unable to persist TinyID audit log on datasource sequence 0", ex);
        }
    }

    /**
     * 将标准操作日志事件转换为独立的 TinyID 审计实体。
     *
     * @param event 标准操作日志事件
     * @return 尚未持久化的审计实体
     */
    private TinyIdAuditLog toAuditLog(OperLogEvent event) {
        TinyIdAuditLog log = new TinyIdAuditLog();
        log.setOperator(event.getOperName());
        log.setAction(event.getTitle());
        log.setRequestMethod(event.getRequestMethod());
        log.setRequestUri(event.getOperUrl());
        log.setSuccess(event.getStatus() != null && event.getStatus() == 0);
        log.setErrorSummary(sanitize(event.getErrorMsg()));
        log.setClientIp(event.getOperIp());
        return log;
    }

    /**
     * 将底层异常信息替换为固定摘要，避免敏感信息进入审计表。
     *
     * @param value 原始错误信息
     * @return 脱敏错误摘要；无错误时返回 {@code null}
     */
    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return "TinyID management request failed";
    }
}
