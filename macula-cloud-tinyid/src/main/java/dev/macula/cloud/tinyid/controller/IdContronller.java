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

package dev.macula.cloud.tinyid.controller;

import dev.macula.boot.result.Result;
import dev.macula.boot.starter.tinyid.base.entity.SegmentId;
import dev.macula.boot.starter.tinyid.base.factory.IdGeneratorFactory;
import dev.macula.boot.starter.tinyid.base.generator.IdGenerator;
import dev.macula.boot.starter.tinyid.base.service.SegmentIdService;
import dev.macula.cloud.tinyid.service.TinyIdTokenService;
import dev.macula.cloud.tinyid.pojo.vo.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供高可靠直连的 TinyID 发号接口，并校验业务类型与应用 Token 的授权关系。
 *
 * @author Rain
 * @since 6.1.0
 */
@RestController
@RequestMapping("/api/v1/id/")
@RequiredArgsConstructor
public class IdContronller {

    /** 日志记录器。 */
    private static final Logger logger = LoggerFactory.getLogger(IdContronller.class);

    /** ID 生成器工厂。 */
    private final IdGeneratorFactory idGeneratorFactory;
    /** 号段申请服务。 */
    private final SegmentIdService segmentIdService;
    /** Token 与业务授权校验服务。 */
    private final TinyIdTokenService tinyIdTokenService;

    /** 单次发号允许返回的最大 ID 数量。 */
    @Value("${macula.tinyid.batch-size-max:100}")
    private Integer batchSizeMax;

    /**
     * 将请求批量大小规范到 1 至系统上限之间。
     *
     * @param batchSize 客户端请求的批量大小
     * @return 实际使用的批量大小
     */
    private Integer checkBatchSize(Integer batchSize) {
        if (batchSize == null) {
            batchSize = 1;
        }
        if (batchSize > batchSizeMax) {
            batchSize = batchSizeMax;
        }
        return batchSize;
    }

    /**
     * 批量获取 ID，并使用统一结果对象返回。
     *
     * @param bizType 业务类型
     * @param batchSize 请求的 ID 数量
     * @param token 应用接入 Token
     * @return ID 列表结果
     */
    @RequestMapping("nextId")
    public Result<List<Long>> nextId(String bizType, Integer batchSize, String token) {
        Result<List<Long>> response;
        Integer newBatchSize = checkBatchSize(batchSize);
        if (!tinyIdTokenService.canVisit(bizType, token)) {
            return Result.failed(ErrorCode.TOKEN_ERR);
        }
        try {
            IdGenerator idGenerator = idGeneratorFactory.getIdGenerator(bizType);
            List<Long> ids = idGenerator.nextId(newBatchSize);
            response = Result.success(ids);
        } catch (Exception e) {
            response = Result.failed(ErrorCode.SYS_ERR);
            logger.error("nextId error", e);
        }
        return response;
    }

    /**
     * 批量获取 ID，并以逗号分隔文本返回。
     *
     * @param bizType 业务类型
     * @param batchSize 请求的 ID 数量
     * @param token 应用接入 Token
     * @return 逗号分隔的 ID；授权或发号失败时返回空字符串
     */
    @RequestMapping("nextIdSimple")
    public String nextIdSimple(String bizType, Integer batchSize, String token) {
        Integer newBatchSize = checkBatchSize(batchSize);
        if (!tinyIdTokenService.canVisit(bizType, token)) {
            return "";
        }
        String response = "";
        try {
            IdGenerator idGenerator = idGeneratorFactory.getIdGenerator(bizType);
            if (newBatchSize == 1) {
                Long id = idGenerator.nextId();
                response = id + "";
            } else {
                List<Long> idList = idGenerator.nextId(newBatchSize);
                StringBuilder sb = new StringBuilder();
                for (Long id : idList) {
                    sb.append(id).append(",");
                }
                response = sb.deleteCharAt(sb.length() - 1).toString();
            }
        } catch (Exception e) {
            logger.error("nextIdSimple error", e);
        }
        return response;
    }

    /**
     * 获取下一个号段，并使用统一结果对象返回。
     *
     * @param bizType 业务类型
     * @param token 应用接入 Token
     * @return 号段结果
     */
    @RequestMapping("nextSegmentId")
    public Result<SegmentId> nextSegmentId(String bizType, String token) {
        Result<SegmentId> response = new Result<>();
        if (!tinyIdTokenService.canVisit(bizType, token)) {
            return Result.failed(ErrorCode.TOKEN_ERR);
        }
        try {
            SegmentId segmentId = segmentIdService.getNextSegmentId(bizType);
            response = Result.success(segmentId);
        } catch (Exception e) {
            response = Result.failed(ErrorCode.SYS_ERR);
            logger.error("nextSegmentId error", e);
        }
        return response;
    }

    /**
     * 获取下一个号段，并以逗号分隔文本返回。
     *
     * @param bizType 业务类型
     * @param token 应用接入 Token
     * @return 号段字段文本；授权或发号失败时返回空字符串
     */
    @RequestMapping("nextSegmentIdSimple")
    public String nextSegmentIdSimple(String bizType, String token) {
        if (!tinyIdTokenService.canVisit(bizType, token)) {
            return "";
        }
        String response = "";
        try {
            SegmentId segmentId = segmentIdService.getNextSegmentId(bizType);
            response =
                segmentId.getCurrentId() + "," + segmentId.getLoadingId() + "," + segmentId.getMaxId() + "," + segmentId.getDelta() + "," + segmentId.getRemainder();
        } catch (Exception e) {
            logger.error("nextSegmentIdSimple error", e);
        }
        return response;
    }

}
