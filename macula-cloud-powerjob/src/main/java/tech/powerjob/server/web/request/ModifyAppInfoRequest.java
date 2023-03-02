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

package tech.powerjob.server.web.request;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import tech.powerjob.common.exception.PowerJobException;
import tech.powerjob.common.utils.CommonUtils;

/**
 * 修改应用信息请求
 *
 * @author tjq
 * @since 2020/4/1
 */
@Data
public class ModifyAppInfoRequest {

    private Long id;
    private String oldPassword;
    private String appName;
    private String password;

    public void valid() {
        CommonUtils.requireNonNull(appName, "appName can't be empty");
        if (StringUtils.containsWhitespace(appName)) {
            throw new PowerJobException("appName can't contains white space!");
        }
    }
}
