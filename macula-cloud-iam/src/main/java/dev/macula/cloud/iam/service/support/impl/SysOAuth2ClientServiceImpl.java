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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientMapper;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        return this.getOne(new LambdaQueryWrapper<SysOAuth2Client>().eq(SysOAuth2Client::getClientId, clientId));
    }
}
