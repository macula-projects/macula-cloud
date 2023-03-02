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
import tech.powerjob.common.utils.CommonUtils;
import tech.powerjob.server.common.constants.ContainerSourceType;
import tech.powerjob.server.common.constants.SwitchableStatus;

/**
 * 保存/修改 容器 请求
 *
 * @author tjq
 * @since 2020/5/15
 */
@Data
public class SaveContainerInfoRequest {

    /**
     * 容器ID，null -> 创建；否则代表修改
     */
    private Long id;

    /**
     * 所属的应用ID
     */
    private Long appId;

    /**
     * 容器名称
     */
    private String containerName;

    /**
     * 容器类型，枚举值为 ContainerSourceType（JarFile/Git）
     */
    private ContainerSourceType sourceType;
    /**
     * 由 sourceType 决定，JarFile -> String，存储文件名称；Git -> JSON，包括 URL，branch，username，password
     */
    private String sourceInfo;

    /**
     * 状态，枚举值为 ContainerStatus（ENABLE/DISABLE）
     */
    private SwitchableStatus status;

    public void valid() {
        CommonUtils.requireNonNull(containerName, "containerName can't be empty");
        CommonUtils.requireNonNull(appId, "appId can't be empty");
        CommonUtils.requireNonNull(sourceInfo, "sourceInfo can't be empty");
    }
}
