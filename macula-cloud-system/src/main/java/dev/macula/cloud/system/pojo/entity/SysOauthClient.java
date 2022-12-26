/*
 * Copyright (c) 2022 Macula
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

package dev.macula.cloud.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import lombok.Data;

/**
 * 客户端实体
 *
 * @author haoxr
 * @date 2022/6/11
 */
@Data
public class SysOauthClient extends BaseEntity {

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 客户端密钥
     */
    private String clientSecret;

    /**
     * 资源id集合
     */
    private String resourceIds;

    /**
     * 作用域
     */
    private String scope;

    /**
     * 授权方式
     */
    private String authorizedGrantTypes;

    /**
     * 回调地址
     */
    private String webServerRedirectUri;

    /**
     * 权限集合
     */
    private String authorities;

    /**
     * 认证令牌时效()
     */
    private Integer accessTokenValidity;

    /**
     * 刷新令牌时效(单位:秒)
     */
    private Integer refreshTokenValidity;

    /**
     * 扩展信息
     */
    private String additionalInformation;

    /**
     * 是否自动放行
     */
    private String autoapprove;

}
