/*
 * Copyright (c) 2026 Macula
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

package dev.macula.cloud.tinyid.service;

import dev.macula.boot.starter.tinyid.base.entity.SegmentId;
import java.util.List;

/**
 * 发号业务入口，负责参数校验、批量约束和号段服务编排。
 * @author Rain
 * @since 6.1.0
 */
public interface TinyIdIssuingService {
    /**
     * @param bizType 业务类型
     * @param batchSize 请求数量，null 使用默认值
     * @return ID 列表
     */
    List<Long> nextIds(String bizType, Integer batchSize);

    /**
     * 网关身份由安全链校验，不使用旧 Token。
     * @param bizType 业务类型
     * @return 号段
     */
    SegmentId nextSegment(String bizType);
}
