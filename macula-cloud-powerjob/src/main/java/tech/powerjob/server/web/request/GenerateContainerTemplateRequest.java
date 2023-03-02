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
 * 创建容器模版请求
 *
 * @author tjq
 * @since 2020/5/15
 */
@Data
public class GenerateContainerTemplateRequest {

    /**
     * Maven Group
     */
    private String group;
    /**
     * Maven artifact
     */
    private String artifact;
    /**
     * Maven name
     */
    private String name;
    /**
     * 包名（com.xx.xx.xx）
     */
    private String packageName;
    /**
     * Java版本号，8或者11
     */
    private Integer javaVersion;

}
