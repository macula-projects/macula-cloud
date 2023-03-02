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

package tech.powerjob.server.remote.transporter;

import tech.powerjob.common.PowerSerializable;
import tech.powerjob.remote.framework.base.RemotingException;
import tech.powerjob.remote.framework.base.URL;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * server 数据传输服务
 *
 * @author tjq
 * @since 2023/1/21
 */
public interface TransportService {

    /**
     * 自用地址，用于维护 server -> appId 和 server 间通讯 4.3.0 前为 ActorSystem Address（ip:10086） 4.3.0 后 PowerJob 将主协议切换为自由协议，默认使用
     * HTTP address （ip:10010）
     *
     * @return 自用地址
     */
    ProtocolInfo defaultProtocol();

    /**
     * 当前支持的全部协议
     *
     * @return allProtocols
     */
    Map<String, ProtocolInfo> allProtocols();

    void tell(String protocol, URL url, PowerSerializable request);

    <T> CompletionStage<T> ask(String protocol, URL url, PowerSerializable request, Class<T> clz)
        throws RemotingException;

}
