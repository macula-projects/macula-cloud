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

package dev.macula.cloud.tinyid.service.impl;

import dev.macula.boot.starter.tinyid.base.entity.SegmentId;
import dev.macula.boot.starter.tinyid.base.factory.IdGeneratorFactory;
import dev.macula.boot.starter.tinyid.base.service.SegmentIdService;
import dev.macula.cloud.tinyid.service.TinyIdIssuingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 执行参数校验与批量限制；已有业务异常直接传播，由统一 Advice 转为响应。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class TinyIdIssuingServiceImpl implements TinyIdIssuingService {

    private final IdGeneratorFactory generatorFactory;
    private final SegmentIdService segmentService;

    @Value("${macula.tinyid.batch-size-max:100}")
    private int batchSizeMax;

    @Override
    public List<Long> nextIds(String bizType, Integer batchSize) {
        requireBizType(bizType);
        int size = batchSize == null ? 1 : Math.min(batchSize, batchSizeMax);
        return generatorFactory.getIdGenerator(bizType).nextId(size);
    }

    @Override
    public SegmentId nextSegment(String bizType) {
        requireBizType(bizType);
        return segmentService.getNextSegmentId(bizType);
    }

    private void requireBizType(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            throw new IllegalArgumentException("bizType must not be blank");
        }
    }
}
