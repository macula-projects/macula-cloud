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

package tech.powerjob.server.remote.server;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;
import tech.powerjob.common.response.AskResponse;
import tech.powerjob.common.serialize.JsonUtils;
import tech.powerjob.remote.framework.actor.Actor;
import tech.powerjob.remote.framework.actor.Handler;
import tech.powerjob.remote.framework.actor.ProcessType;
import tech.powerjob.server.remote.server.election.Ping;
import tech.powerjob.server.remote.server.redirector.RemoteProcessReq;
import tech.powerjob.server.remote.server.redirector.RemoteRequestProcessor;
import tech.powerjob.server.remote.transporter.TransportService;

import static tech.powerjob.common.RemoteConstant.*;

/**
 * 处理朋友们的信息（处理服务器与服务器之间的通讯）
 *
 * @author tjq
 * @since 2020/4/9
 */
@Slf4j
@Component
@Actor(path = S4S_PATH)
public class FriendActor {

    private final TransportService transportService;

    public FriendActor(TransportService transportService) {
        this.transportService = transportService;
    }

    /**
     * 处理存活检测的请求
     */
    @Handler(path = S4S_HANDLER_PING, processType = ProcessType.NO_BLOCKING)
    public AskResponse onReceivePing(Ping ping) {
        return AskResponse.succeed(transportService.allProtocols());
    }

    @Handler(path = S4S_HANDLER_PROCESS, processType = ProcessType.BLOCKING)
    public AskResponse onReceiveRemoteProcessReq(RemoteProcessReq req) {

        AskResponse response = new AskResponse();
        response.setSuccess(true);
        try {
            response.setData(JsonUtils.toBytes(RemoteRequestProcessor.processRemoteRequest(req)));
        } catch (Throwable t) {
            log.error("[FriendActor] process remote request[{}] failed!", req, t);
            response.setSuccess(false);
            response.setMessage(ExceptionUtils.getMessage(t));
        }
        return response;
    }
}
