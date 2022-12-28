/*
 * Copyright (c) 2022 Macula
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

import dev.macula.cloud.system.service.SysPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * {@code ApplicationListener} 应用启动事件监听
 *
 * @author rain
 * @since 2022/12/28 15:45
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationListener {
    private final SysPermissionService sysPermissionService;

    @EventListener(ApplicationStartedEvent.class)
    public void onStarted(ApplicationStartedEvent event) {
        sysPermissionService.refreshPermRolesRules();
        log.info("Url permission and roles refreshed");
    }
}
