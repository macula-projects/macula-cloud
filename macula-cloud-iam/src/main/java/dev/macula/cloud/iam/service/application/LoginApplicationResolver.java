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
package dev.macula.cloud.iam.service.application;

import dev.macula.cloud.system.enums.IdentityLoginCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;

/**
 * 登录请求到中立应用的端口；协议参数校验由当前协议适配器完成。
 * 旧reference只代表服务端保存的OAuth client_id，不能套用未来其他协议标识。
 * @author Rain
 * @since 6.1.0
 */
public interface LoginApplicationResolver {
    /** 只解析Session内受控请求；没有请求返回空，非法声明必须拒绝而非门户降级。 */
    Optional<Selection> resolve(HttpServletRequest request, HttpServletResponse response);

    /** 解析已验证流程保存的引用；lock要求调用者可写事务，能力许可由协议适配器核验。 */
    IamApplicationSnapshot trusted(String reference, IdentityLoginCapability capability, boolean lock);

    /**
     * 单次请求的可信引用及中立应用，不写入或改变原Session/Token证明格式。
     * @param reference 当前适配器的服务端规范引用
     * @param application 中立应用快照
     * @since 6.1.0
     */
    record Selection(String reference, IamApplicationSnapshot application) { }
}
