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

import dev.macula.cloud.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Canal + RabbitMQ 监听数据库数据变化
 *
 * @author haoxr
 * @date 2021/11/4 23:14
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CanalListener {

    private final SysMenuService menuService;

//    @RabbitListener(queues = "canal.queue")
//    public void handleDataChange(@Payload CanalMessage message) {
//        String tableName = message.getTable();
//
//        log.info("Canal 监听 {} 发生变化；明细：{}", tableName, message);
//        if (Arrays.asList("sys_menu", "sys_role", "sys_role_menu").contains(tableName)) {
//            log.info("======== 清理菜单路由缓存 ========");
//            menuService.cleanCache();
//        }
//    }
}
