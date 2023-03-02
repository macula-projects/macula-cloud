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

package tech.powerjob.server.web.response;

import lombok.Data;

import java.util.Date;

/**
 * 容器信息 视图层展示对象
 *
 * @author tjq
 * @since 2020/5/15
 */
@Data
public class ContainerInfoVO {

    private Long id;

    private String containerName;

    /**
     * 容器类型，枚举值为 ContainerSourceType
     */
    private String sourceType;
    /**
     * 由 sourceType 决定，JarFile -> String，存储文件名称；Git -> JSON，包括 URL，branch，username，password
     */
    private String sourceInfo;
    /**
     * 版本 （Jar包使用md5，Git使用commitId，前者32位，后者40位，不会产生碰撞）
     */
    private String version;
    /**
     * 状态，枚举值为 ContainerStatus
     */
    private String status;
    /**
     * 上一次部署时间
     */
    private String lastDeployTime;

    private Date gmtCreate;
    private Date gmtModified;
}
