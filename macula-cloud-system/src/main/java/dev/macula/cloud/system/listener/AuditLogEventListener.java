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

package dev.macula.cloud.system.listener;

import dev.macula.boot.starter.auditlog.event.OperLogEvent;
import dev.macula.cloud.system.pojo.entity.SysLog;
import dev.macula.cloud.system.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * {@code AuditLogEventListener} 审计日志监听
 *
 * @author rain
 * @since 2023/9/6 10:12
 */
@Component
@RequiredArgsConstructor
public class AuditLogEventListener {

    private final SysLogService sysLogService;

    /**
     * 保存系统日志记录
     */
    @Async
    @EventListener
    public void saveLog(OperLogEvent operLogEvent) {
        SysLog log = new SysLog();
        log.setOpIp(operLogEvent.getOperIp());
        log.setErrorMsg(operLogEvent.getErrorMsg());
        log.setJsonResult(operLogEvent.getJsonResult());
        log.setOpName(operLogEvent.getOperName());
        log.setOpParam(operLogEvent.getOperParam());
        log.setOpMethod(operLogEvent.getMethod());
        log.setOpStatus(operLogEvent.getStatus());
        log.setOpUrl(operLogEvent.getOperUrl());
        log.setOpRequestMethod(operLogEvent.getRequestMethod());
        log.setOpTitle(operLogEvent.getTitle());
        log.setCreateBy(operLogEvent.getOperName());
        if (operLogEvent.getOperTime() != null) {
            log.setCreateTime(LocalDateTime.ofInstant(operLogEvent.getOperTime().toInstant(), ZoneId.systemDefault()));
        } else {
            log.setCreateTime(LocalDateTime.now());
        }
        sysLogService.save(log);
    }
}
