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

package tech.powerjob.server.core.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.powerjob.common.exception.PowerJobException;
import tech.powerjob.server.persistence.remote.model.AppInfoDO;
import tech.powerjob.server.persistence.remote.repository.AppInfoRepository;

import java.util.Objects;

/**
 * 应用信息服务
 *
 * @author tjq
 * @since 2020/6/20
 */
@Service
@RequiredArgsConstructor
public class AppInfoService {

    private final AppInfoRepository appInfoRepository;

    /**
     * 验证应用访问权限
     *
     * @param appName  应用名称
     * @param password 密码
     * @return 应用ID
     */
    public Long assertApp(String appName, String password) {

        AppInfoDO appInfo = appInfoRepository.findByAppName(appName)
            .orElseThrow(() -> new PowerJobException("can't find appInfo by appName: " + appName));
        if (Objects.equals(appInfo.getPassword(), password)) {
            return appInfo.getId();
        }
        throw new PowerJobException("password error!");
    }

}
