package org.macula.cloud.tinyid.controller;

import org.macula.boot.starter.api.Result;
import org.macula.boot.starter.tinyid.base.entity.SegmentId;
import org.macula.boot.starter.tinyid.base.factory.IdGeneratorFactory;
import org.macula.boot.starter.tinyid.base.generator.IdGenerator;
import org.macula.boot.starter.tinyid.base.service.SegmentIdService;
import org.macula.cloud.tinyid.service.TinyIdTokenService;
import org.macula.cloud.tinyid.vo.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author du_imba
 */
@RestController
@RequestMapping("/id/")
public class IdContronller {

    private static final Logger logger = LoggerFactory.getLogger(IdContronller.class);
    @Autowired
    private IdGeneratorFactory idGeneratorFactory;
    @Autowired
    private SegmentIdService segmentIdService;
    @Autowired
    private TinyIdTokenService tinyIdTokenService;
    @Value("${macula.tinyid.batch-size-max:100}")
    private Integer batchSizeMax;

    private Integer checkBatchSize(Integer batchSize) {
        if (batchSize == null) {
            batchSize = 1;
        }
        if (batchSize > batchSizeMax) {
            batchSize = batchSizeMax;
        }
        return batchSize;
    }

    @RequestMapping("nextId")
    public Result<List<Long>> nextId(String bizType, Integer batchSize, String token) {
        Result<List<Long>> response = new Result<>();
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

    @RequestMapping("nextSegmentIdSimple")
    public String nextSegmentIdSimple(String bizType, String token) {
        if (!tinyIdTokenService.canVisit(bizType, token)) {
            return "";
        }
        String response = "";
        try {
            SegmentId segmentId = segmentIdService.getNextSegmentId(bizType);
            response = segmentId.getCurrentId() + "," + segmentId.getLoadingId() + "," + segmentId.getMaxId()
                    + "," + segmentId.getDelta() + "," + segmentId.getRemainder();
        } catch (Exception e) {
            logger.error("nextSegmentIdSimple error", e);
        }
        return response;
    }

}
