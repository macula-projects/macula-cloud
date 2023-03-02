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

/**
 * 创建/修改 UserInfo 请求
 *
 * @author tjq
 * @since 2020/4/12
 */
@Data
public class ModifyUserInfoRequest {

    private Long id;

    private String username;
    private String password;
    private String webHook;

    /**
     * 手机号
     */
    private String phone;
    /**
     * 邮箱地址
     */
    private String email;
}
