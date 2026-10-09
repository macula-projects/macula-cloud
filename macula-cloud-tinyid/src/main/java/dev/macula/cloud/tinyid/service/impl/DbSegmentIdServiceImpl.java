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

package dev.macula.cloud.tinyid.service.impl;

import dev.macula.boot.starter.tinyid.base.entity.SegmentId;
import dev.macula.boot.starter.tinyid.base.exception.TinyIdSysException;
import dev.macula.boot.starter.tinyid.base.service.SegmentIdService;
import dev.macula.cloud.tinyid.common.Constants;
import dev.macula.cloud.tinyid.mapper.TinyIdInfoMapper;
import dev.macula.cloud.tinyid.pojo.entity.TinyIdInfo;
import dev.macula.cloud.tinyid.pojo.vo.ErrorCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 在事务内通过乐观锁申请号段，并以统一业务异常报告失败。
 *
 * @author du_imba
 * @since 6.1.0
 */
@Component
public class DbSegmentIdServiceImpl implements SegmentIdService {

    private static final Logger logger = LoggerFactory.getLogger(DbSegmentIdServiceImpl.class);

    /** 发号业务 Mapper。 */
    private final TinyIdInfoMapper infoMapper;

    /**
     * 创建数据库号段服务。
     *
     * @param infoMapper 发号业务 Mapper
     */
    public DbSegmentIdServiceImpl(TinyIdInfoMapper infoMapper) {
        this.infoMapper = infoMapper;
    }

    /**
     * Transactional标记保证query和update使用的是同一连接
     * 事务隔离级别应该为READ_COMMITTED,Spring默认是DEFAULT(取决于底层使用的数据库，mysql的默认隔离级别为REPEATABLE_READ)
     * <p>
     * 如果是REPEATABLE_READ，那么在本次事务中循环查询 TinyIdInfo 获取的结果不会变化，也就查询不到其他事务提交的内容，
     * 后续基于旧版本号更新 maxId 将持续失败。
     *
     * @param bizType 业务类型
     * @return SegmentId
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SegmentId getNextSegmentId(String bizType) {
        try {
            return allocateSegment(bizType);
        } catch (TinyIdSysException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TinyIdSysException(ErrorCode.SYS_ERR, "号段申请失败", e);
        }
    }

    private SegmentId allocateSegment(String bizType) {
        // 获取nextTinyId的时候，有可能存在version冲突，需要重试
        for (int i = 0; i < Constants.RETRY; i++) {
            TinyIdInfo tinyIdInfo = infoMapper.selectOne(
                Wrappers.<TinyIdInfo>lambdaQuery().eq(TinyIdInfo::getBizType, bizType));
            if (tinyIdInfo == null) {
                throw new TinyIdSysException(ErrorCode.BIZ_TYPE_NOT_FOUND, "发号业务不存在");
            }
            Long newMaxId = tinyIdInfo.getMaxId() + tinyIdInfo.getStep();
            Long oldMaxId = tinyIdInfo.getMaxId();
            int row = infoMapper.updateMaxId(tinyIdInfo.getId(), newMaxId, oldMaxId, tinyIdInfo.getVersion(),
                tinyIdInfo.getBizType());
            if (row == 1) {
                tinyIdInfo.setMaxId(newMaxId);
                SegmentId segmentId = convert(tinyIdInfo);
                logger.info("getNextSegmentId success tinyIdInfo:{} current:{}", tinyIdInfo, segmentId);
                return segmentId;
            } else {
                logger.info("getNextSegmentId conflict tinyIdInfo:{}", tinyIdInfo);
            }
        }
        throw new TinyIdSysException(ErrorCode.SEGMENT_CONFLICT, "号段更新冲突，请稍后重试");
    }

    public SegmentId convert(TinyIdInfo idInfo) {
        SegmentId segmentId = new SegmentId();
        segmentId.setCurrentId(new AtomicLong(idInfo.getMaxId() - idInfo.getStep()));
        segmentId.setMaxId(idInfo.getMaxId());
        segmentId.setRemainder(idInfo.getRemainder() == null ? 0 : idInfo.getRemainder());
        segmentId.setDelta(idInfo.getDelta() == null ? 1 : idInfo.getDelta());
        // 默认20%加载
        segmentId.setLoadingId(segmentId.getCurrentId().get() + (long)idInfo.getStep() * Constants.LOADING_PERCENT / 100);
        return segmentId;
    }
}
