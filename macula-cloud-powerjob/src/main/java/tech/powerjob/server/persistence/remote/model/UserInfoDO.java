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

package tech.powerjob.server.persistence.remote.model;

import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Date;

/**
 * 用户信息表
 *
 * @author tjq
 * @since 2020/4/12
 */
@Data
@Entity
@Table(indexes = {@Index(name = "uidx01_user_info", columnList = "username"),
    @Index(name = "uidx02_user_info", columnList = "email")})
public class UserInfoDO {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private Long id;

    private String username;

    private String password;
    /**
     * 手机号
     */
    private String phone;
    /**
     * 邮箱地址
     */
    private String email;
    /**
     * webHook
     */
    private String webHook;
    /**
     * 扩展字段
     */
    private String extra;

    private Date gmtCreate;

    private Date gmtModified;
}
