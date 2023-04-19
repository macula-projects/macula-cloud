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

import cn.hutool.core.util.StrUtil;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.iam.authentication.weapp.WeappSessionKeyCache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * {@code WeappSessionKeyCacheImpl} SESSION KEY 缓存实现
 *
 * @author rain
 * @since 2023/4/12 19:48
 */
@RequiredArgsConstructor
public class WeappSessionKeyCacheImpl implements WeappSessionKeyCache {
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void put(String cacheKey, String sessionKey) {
        redisTemplate.opsForValue().set(getKey(cacheKey), sessionKey);
    }

    @Override
    public String get(String cacheKey) {
        return (String)redisTemplate.opsForValue().get(getKey(cacheKey));
    }

    private String getKey(String cacheKey) {
        return StrUtil.format("{}:{}", CacheConstants.OAUTH2_TOKEN_WEAPP_SESSION_KEY, cacheKey);
    }
}
