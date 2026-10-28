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

package dev.macula.cloud.iam.service.support.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientMapper;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code SysOauth2ClientServiceImpl} 客户端服务
 *
 * @author rain
 * @since 2023/4/11 19:42
 */
@Service
@RequiredArgsConstructor
public class SysOAuth2ClientServiceImpl extends ServiceImpl<SysOAuth2ClientMapper, SysOAuth2Client>
    implements SysOAuth2ClientService {

    @Override
    public SysOAuth2Client getClientByClientId(String clientId) {
        return this.getOne(new QueryWrapper<SysOAuth2Client>().eq("client_id", clientId));
    }

    @Override
    @Cacheable(value = OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX, key = "'__allowed_origins__'", sync = true)
    public Set<String> allowedOrigins() {
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        this.list(new QueryWrapper<SysOAuth2Client>()
                .select("allowed_origins")
                .isNotNull("allowed_origins")
                .ne("allowed_origins", ""))
            .stream()
            .map(SysOAuth2Client::getAllowedOrigins)
            .filter(StringUtils::hasText)
            .forEach(value -> origins.addAll(StringUtils.commaDelimitedListToSet(value)));
        return Collections.unmodifiableSet(origins);
    }

    @Override
    public boolean isAllowedOrigin(String clientId, String origin) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(origin)) {
            return false;
        }
        SysOAuth2Client client = getClientByClientId(clientId);
        return client != null && StringUtils.commaDelimitedListToSet(client.getAllowedOrigins()).contains(origin);
    }
}
