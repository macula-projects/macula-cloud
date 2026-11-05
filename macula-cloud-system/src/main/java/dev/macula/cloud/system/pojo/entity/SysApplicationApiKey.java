/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

import com.baomidou.mybatisplus.annotation.TableName;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import lombok.Data;
import lombok.ToString;

/**
 * 应用独立凭据及可选 URL 配置，撤销记录保留用于缓存重建清理。
 * @author Rain
 * @since 6.1.0
 */
@Data
@TableName("sys_application_apikey_tenant")
public class SysApplicationApiKey extends BaseEntity {
    private Long tenantId;
    private Long applicationId;
    @ToString.Exclude
    private String apiKey;
    private String remark;
    private String accessPath;
    private Boolean deleted;
}
