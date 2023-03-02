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

package tech.powerjob.server.persistence.local;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.powerjob.common.enums.LogLevel;

import javax.persistence.*;

/**
 * 本地的运行时日志
 *
 * @author tjq
 * @since 2020/4/27
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "local_instance_log", indexes = {@Index(columnList = "instanceId")})
public class LocalInstanceLogDO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long instanceId;
    /**
     * 日志时间
     */
    private Long logTime;
    /**
     * 日志级别 {@link LogLevel}
     */
    private Integer logLevel;
    /**
     * 日志内容
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String logContent;

    /**
     * 机器地址
     */
    private String workerAddress;
}
