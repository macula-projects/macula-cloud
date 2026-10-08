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

import dev.macula.cloud.tinyid.config.DynamicDataSource;
import dev.macula.cloud.tinyid.mapper.TinyIdInfoMapper;
import dev.macula.cloud.tinyid.mapper.TinyIdTokenMapper;
import dev.macula.cloud.tinyid.pojo.entity.TinyIdInfo;
import dev.macula.cloud.tinyid.pojo.entity.TinyIdToken;
import dev.macula.cloud.tinyid.service.TinyIdTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Maintains the immutable in-memory application and business authorization snapshot.
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class TinyIdTokenServiceImpl implements TinyIdTokenService {
    /** 日志记录器。 */
    private static final Logger logger = LoggerFactory.getLogger(TinyIdTokenServiceImpl.class);
    /** Token 到授权业务集合的不可变缓存快照。 */
    private volatile Map<String, Set<String>> token2bizTypes = Map.of();
    /** 应用授权 Mapper。 */
    private final TinyIdTokenMapper tokenMapper;
    /** 发号业务 Mapper。 */
    private final TinyIdInfoMapper infoMapper;
    /** 发号随机路由与管理显式路由共用的数据源。 */
    private final DynamicDataSource routingDataSource;

    /**
     * 创建 Token 授权缓存服务。
     *
     * @param tokenMapper 应用授权 Mapper
     * @param infoMapper 发号业务 Mapper
     * @param routingDataSource 动态路由数据源
     */
    public TinyIdTokenServiceImpl(TinyIdTokenMapper tokenMapper, TinyIdInfoMapper infoMapper,
        DynamicDataSource routingDataSource) {
        this.tokenMapper = tokenMapper;
        this.infoMapper = infoMapper;
        this.routingDataSource = routingDataSource;
    }

    /**
     * 查询当前路由数据源中的全部 Token 记录。
     *
     * @return Token 记录列表
     */
    public List<TinyIdToken> queryAll() {
        return tokenMapper.selectList(null);
    }

    /** 每分钟从数据库重建一次 Token 授权缓存。 */
    @Scheduled(cron = "0 0/1 * * * ?")
    public void refresh() {
        logger.info("refresh token begin");
        refreshCache();
    }

    /** 应用启动后初始化 Token 授权缓存。 */
    @PostConstruct
    public void init() {
        refreshCache();
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void refreshCache() {
        logger.info("tinyId token init begin");
        Map<String, Set<String>> loaded = loadAllAuthorizations();
        Map<String, Set<String>> immutable = new HashMap<>();
        loaded.forEach((token, bizTypes) -> immutable.put(token, Set.copyOf(bizTypes)));
        token2bizTypes = Map.copyOf(immutable);
        logger.info("tinyId token init success, token size:{}", token2bizTypes.size());
    }

    /**
     * 从全部物理数据源加载 Token 授权并取并集。
     *
     * @return Token 到业务类型集合的映射
     */
    private Map<String, Set<String>> loadAllAuthorizations() {
        Map<String, Set<String>> result = new HashMap<>();
        Map<String, Set<String>> remarks = new HashMap<>();
        Map<String, List<String>> businessConfigurations = new HashMap<>();
        for (int sequence = 0; sequence < routingDataSource.size(); sequence++) {
            List<TinyIdToken> tokens = routingDataSource.execute(sequence, () -> tokenMapper.selectList(null));
            tokens.forEach(token -> {
                result.computeIfAbsent(token.getToken(), ignored -> new HashSet<>()).add(token.getBizType());
                remarks.computeIfAbsent(token.getToken(), ignored -> new HashSet<>()).add(token.getRemark());
            });
            List<TinyIdInfo> businesses = routingDataSource.execute(sequence, () -> infoMapper.selectList(null));
            businesses.forEach(business -> businessConfigurations
                .computeIfAbsent(business.getBizType(), ignored -> new ArrayList<>())
                .add(business.getDelta() + ":" + business.getRemainder()));
        }
        long inconsistentRemarks = remarks.values().stream().filter(values -> values.size() > 1).count();
        long inconsistentBusinesses = businessConfigurations.values().stream()
            .filter(values -> isBusinessConfigurationInconsistent(values, routingDataSource.size())).count();
        if (inconsistentRemarks > 0 || inconsistentBusinesses > 0) {
            logger.warn("TinyID datasource consistency warning: applicationRemarkConflicts={}, businessConflictsOrGaps={}",
                inconsistentRemarks, inconsistentBusinesses);
        }
        return result;
    }

    /**
     * 判断跨数据源业务分片配置是否缺失或冲突。
     *
     * @param values delta 与 remainder 组合值
     * @param dataSourceCount 当前数据源总数
     * @return 配置不一致时返回 {@code true}
     */
    private boolean isBusinessConfigurationInconsistent(List<String> values, int dataSourceCount) {
        if (values.size() != dataSourceCount) {
            return true;
        }
        Set<String> deltas = values.stream().map(value -> value.split(":")[0]).collect(java.util.stream.Collectors.toSet());
        if (deltas.size() != 1) {
            return true;
        }
        int delta = Integer.parseInt(deltas.iterator().next());
        Set<Integer> remainders = values.stream().map(value -> Integer.parseInt(value.split(":")[1]))
            .collect(java.util.stream.Collectors.toSet());
        return remainders.size() != values.size()
            || remainders.stream().anyMatch(remainder -> remainder < 0 || remainder >= delta);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void replaceAuthorizations(String token, Collection<String> bizTypes) {
        Map<String, Set<String>> updated = new HashMap<>(token2bizTypes);
        if (bizTypes == null || bizTypes.isEmpty()) {
            updated.remove(token);
        } else {
            updated.put(token, Set.copyOf(bizTypes));
        }
        token2bizTypes = Map.copyOf(updated);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void removeToken(String token) {
        Map<String, Set<String>> updated = new HashMap<>(token2bizTypes);
        updated.remove(token);
        token2bizTypes = Map.copyOf(updated);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void removeBusiness(String bizType) {
        Map<String, Set<String>> updated = new HashMap<>();
        token2bizTypes.forEach((token, bizTypes) -> {
            Set<String> retained = new HashSet<>(bizTypes);
            retained.remove(bizType);
            if (!retained.isEmpty()) {
                updated.put(token, Set.copyOf(retained));
            }
        });
        token2bizTypes = Map.copyOf(updated);
    }

    /** {@inheritDoc} */
    @Override
    public boolean canVisit(String bizType, String token) {
        if (ObjectUtils.isEmpty(bizType) || ObjectUtils.isEmpty(token)) {
            return false;
        }
        Set<String> bizTypes = token2bizTypes.get(token);
        return (bizTypes != null && bizTypes.contains(bizType));
    }

    /**
     * 将 Token 行列表转换为 Token 到业务集合的映射。
     *
     * @param list Token 数据库记录
     * @return Token 授权映射
     */
    public Map<String, Set<String>> converToMap(List<TinyIdToken> list) {
        Map<String, Set<String>> map = new HashMap<>(64);
        if (list != null) {
            for (TinyIdToken tinyIdToken : list) {
                if (!map.containsKey(tinyIdToken.getToken())) {
                    map.put(tinyIdToken.getToken(), new HashSet<>());
                }
                map.get(tinyIdToken.getToken()).add(tinyIdToken.getBizType());
            }
        }
        return map;
    }

}
