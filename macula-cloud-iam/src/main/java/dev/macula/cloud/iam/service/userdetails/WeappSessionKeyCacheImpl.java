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

package dev.macula.cloud.iam.service.userdetails;

import dev.macula.cloud.iam.authentication.weapp.WeappSessionKeyCache;

/**
 * {@code WeappSessionKeyCacheImpl} SESSION KEY 缓存实现
 *
 * @author rain
 * @since 2023/4/12 19:48
 */
public class WeappSessionKeyCacheImpl implements WeappSessionKeyCache {
    @Override
    public String put(String cacheKey, String sessionKey) {
        // TODO 缓存实现
        return null;
    }

    @Override
    public String get(String cacheKey) {
        return null;
    }
}
