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

/**
 * 按内部主键读取公共应用策略的端口，不以OAuth client_id冒充通用协议标识。
 * @author Rain
 * @since 6.1.0
 */
public interface IamApplicationService {
    /** 在同一RR事务中读取公共应用、唯一协议及来源；应用不存在返回null。 */
    IamApplicationSnapshot findById(Long applicationId);

    /** 在调用者的可写事务中按主应用、协议、来源顺序锁定并重读当前配置。 */
    IamApplicationSnapshot lock(Long applicationId);
}
