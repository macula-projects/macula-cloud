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

package tech.powerjob.server.web.websocket;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;
import tech.powerjob.server.config.OmsEndpointConfigure;
import tech.powerjob.server.core.container.ContainerService;

import javax.annotation.Resource;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

/**
 * 容器部署 WebSocket 服务 记录一个不错的 WebSocket 测试网站：<a>http://www.easyswoole.com/wstool.html</a>
 *
 * @author tjq
 * @since 2020/5/17
 */
@Slf4j
@Component
@ServerEndpoint(value = "/container/deploy/{id}", configurator = OmsEndpointConfigure.class)
public class ContainerDeployServerEndpoint {

    @Resource
    private ContainerService containerService;

    @OnOpen
    public void onOpen(@PathParam("id") Long id, Session session) {

        RemoteEndpoint.Async remote = session.getAsyncRemote();
        remote.sendText("SYSTEM: connected successfully, start to deploy container: " + id);
        try {
            containerService.deploy(id, session);
        } catch (Exception e) {
            log.error("[ContainerDeployServerEndpoint] deploy container {} failed.", id, e);

            remote.sendText("SYSTEM: deploy failed because of the exception");
            remote.sendText(ExceptionUtils.getStackTrace(e));
        }
        try {
            session.close();
        } catch (Exception e) {
            log.error("[ContainerDeployServerEndpoint] close session for {} failed.", id, e);
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        try {
            session.close();
        } catch (IOException e) {
            log.error("[ContainerDeployServerEndpoint] close session failed.", e);
        }
        log.warn("[ContainerDeployServerEndpoint] session onError!", throwable);
    }
}
