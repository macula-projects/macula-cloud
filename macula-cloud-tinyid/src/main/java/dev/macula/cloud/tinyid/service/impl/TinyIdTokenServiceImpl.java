/*
 * Copyright (c) 2022 Macula
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

import dev.macula.cloud.tinyid.service.TinyIdTokenService;
import dev.macula.cloud.tinyid.dao.TinyIdTokenDAO;
import dev.macula.cloud.tinyid.dao.entity.TinyIdToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * @author du_imba
 */
@Component
public class TinyIdTokenServiceImpl implements TinyIdTokenService {
    private static final Logger logger = LoggerFactory.getLogger(TinyIdTokenServiceImpl.class);
    private static Map<String, Set<String>> token2bizTypes = new HashMap<>();
    private TinyIdTokenDAO tinyIdTokenDAO;

    public TinyIdTokenServiceImpl(TinyIdTokenDAO tinyIdTokenDAO) {
        this.tinyIdTokenDAO = tinyIdTokenDAO;
    }

    public List<TinyIdToken> queryAll() {
        return tinyIdTokenDAO.selectAll();
    }

    /**
     * 1分钟刷新一次token
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    public void refresh() {
        logger.info("refresh token begin");
        init();
    }

    @PostConstruct
    private synchronized void init() {
        logger.info("tinyId token init begin");
        List<TinyIdToken> list = queryAll();
        Map<String, Set<String>> map = converToMap(list);
        token2bizTypes = map;
        logger.info("tinyId token init success, token size:{}", list == null ? 0 : list.size());
    }

    @Override
    public boolean canVisit(String bizType, String token) {
        if (ObjectUtils.isEmpty(bizType) || ObjectUtils.isEmpty(token)) {
            return false;
        }
        Set<String> bizTypes = token2bizTypes.get(token);
        return (bizTypes != null && bizTypes.contains(bizType));
    }

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
