/*
 * Copyright (c) 2026 Macula
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
package dev.macula.cloud.system.protocol;

import java.time.Instant;

/**
 * 单协议登记的非秘密视图；credential_hash仅保留在受保护的持久化对象。
 * schemaVersion=0只表示不可执行的历史未分配槽，不暴露历史快照为Settings。
 * @param protocol 稳定协议代码；未分配槽为null
 * @param schemaVersion 配置版本
 * @param enabled 是否启用
 * @param protocolIdentifier 协议标识
 * @param identifierIssuedAt 原始标识签发时间
 * @param settings 协议非秘密配置；未分配槽为null
 * @param <T> 已注册协议设置类型
 * @author Rain
 * @since 6.1.0
 */
public record IamApplicationProtocolRegistration<T>(String protocol, int schemaVersion, boolean enabled,
    String protocolIdentifier, Instant identifierIssuedAt, T settings) {

    /** 未分配槽永不执行，已分配协议必须有正版本和类型化设置。 */
    public IamApplicationProtocolRegistration {
        if (protocol == null) {
            if (schemaVersion != 0 || enabled || settings != null) {
                throw new IllegalArgumentException("未分配协议登记不能启用或携带运行设置");
            }
        } else if (protocol.isBlank() || schemaVersion < 1 || settings == null) {
            throw new IllegalArgumentException("协议登记结构无效");
        }
    }

    /** 明确区分历史保留槽和已选协议，不能按标识存在与否判断可执行性。 */
    public boolean assigned() {
        return protocol != null;
    }
}
