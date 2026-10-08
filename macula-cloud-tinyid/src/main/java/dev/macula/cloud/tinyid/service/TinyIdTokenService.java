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

package dev.macula.cloud.tinyid.service;

import java.util.Collection;

/**
 * Validates application access to TinyID businesses and refreshes the authorization snapshot.
 *
 * @author Rain
 * @since 6.1.0
 */
public interface TinyIdTokenService {
    /**
     * 是否有权限
     *
     * @param bizType 业务类型
     * @param token   TOKEN
     * @return boolean 权限
     */
    boolean canVisit(String bizType, String token);

    /**
     * Immediately rebuilds the immutable authorization cache.
     */
    void refreshCache();

    /**
     * Atomically replaces one application's authorization set in the current instance.
     *
     * @param token application token
     * @param bizTypes complete authorized business set
     */
    void replaceAuthorizations(String token, Collection<String> bizTypes);

    /**
     * Atomically removes an application from the current instance.
     *
     * @param token application token
     */
    void removeToken(String token);

    /**
     * Atomically removes one business authorization from every application in the current instance.
     *
     * @param bizType deleted business type
     */
    void removeBusiness(String bizType);
}
