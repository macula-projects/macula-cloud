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

package dev.macula.cloud.tinyid.controller;

import dev.macula.boot.starter.tinyid.base.entity.SegmentId;
import dev.macula.cloud.tinyid.service.TinyIdIssuingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TinyID 发号协议适配；发号由 Service 完成，认证由安全链校验，响应及异常由统一 Advice 处理。
 *
 * @author Rain
 * @since 6.1.0
 */
@RestController
@RequestMapping("/api/v1/id/")
@RequiredArgsConstructor
public class IdContronller {

    private final TinyIdIssuingService issuingService;

    /**
     * 批量取号，普通请求由 Advice 包装，Feign 请求返回原始列表。
     * @param bizType 业务类型
     * @param batchSize 批量大小
     * @return ID 列表
     */
    @RequestMapping("nextId")
    public List<Long> nextId(String bizType, Integer batchSize) {
        return issuingService.nextIds(bizType, batchSize);
    }

    /**
     * 将批量取号结果编码为文本，失败交给统一异常处理器。
     * @param bizType 业务类型
     * @param batchSize 批量大小
     * @return 逗号分隔文本
     */
    @RequestMapping("nextIdSimple")
    public ResponseEntity<String> nextIdSimple(String bizType, Integer batchSize) {
        String ids = issuingService.nextIds(bizType, batchSize).stream()
            .map(String::valueOf).collect(Collectors.joining(","));
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(ids);
    }

    /**
     * 申请号段。
     * @param bizType 业务类型
     * @return 号段
     */
    @RequestMapping("nextSegmentId")
    public SegmentId nextSegmentId(String bizType) {
        return issuingService.nextSegment(bizType);
    }

    /**
     * 经网关认证申请号段。
     * @param bizType 业务类型
     * @return 五字段号段文本
     */
    @PostMapping("nextSegmentIdSimple")
    public ResponseEntity<String> nextSegmentIdSimple(@RequestParam("bizType") String bizType) {
        SegmentId segment = issuingService.nextSegment(bizType);
        String body = segment.getCurrentId() + "," + segment.getLoadingId() + "," + segment.getMaxId()
            + "," + segment.getDelta() + "," + segment.getRemainder();
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(body);
    }
}
