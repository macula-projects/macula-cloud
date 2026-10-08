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
import dev.macula.cloud.tinyid.pojo.entity.TinyIdToken;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests atomic authorization snapshot refresh behavior.
 *
 * @author Rain
 * @since 6.1.0
 */
class TinyIdTokenServiceImplTest {

    @Test
    void refreshesAuthorizationUnionAsOneSnapshot() {
        TinyIdTokenMapper mapper = mock(TinyIdTokenMapper.class);
        when(mapper.selectList(null)).thenReturn(List.of(
            authorization("app-token", "order"), authorization("app-token", "member")));
        TinyIdTokenServiceImpl service = service(mapper);

        service.refreshCache();

        assertTrue(service.canVisit("order", "app-token"));
        assertTrue(service.canVisit("member", "app-token"));
        assertFalse(service.canVisit("inventory", "app-token"));
        assertFalse(service.canVisit("order", "unknown-token"));
    }

    @Test
    void keepsPreviousSnapshotWhenRefreshFails() {
        TinyIdTokenMapper mapper = mock(TinyIdTokenMapper.class);
        when(mapper.selectList(null)).thenReturn(List.of(authorization("app-token", "order")))
            .thenThrow(new IllegalStateException("datasource unavailable"));
        TinyIdTokenServiceImpl service = service(mapper);
        service.refreshCache();

        assertThrows(IllegalStateException.class, service::refreshCache);
        assertTrue(service.canVisit("order", "app-token"));
    }

    @Test
    void updatesOnlyCurrentInstanceUntilAnotherInstanceRefreshes() {
        TinyIdTokenMapper firstMapper = mock(TinyIdTokenMapper.class);
        TinyIdTokenMapper secondMapper = mock(TinyIdTokenMapper.class);
        when(firstMapper.selectList(null)).thenReturn(List.of(authorization("app-token", "order")));
        when(secondMapper.selectList(null)).thenReturn(List.of(authorization("app-token", "order")))
            .thenReturn(List.of());
        TinyIdTokenServiceImpl first = service(firstMapper);
        TinyIdTokenServiceImpl second = service(secondMapper);
        first.refreshCache();
        second.refreshCache();

        first.removeToken("app-token");

        assertFalse(first.canVisit("order", "app-token"));
        assertTrue(second.canVisit("order", "app-token"));
        second.refreshCache();
        assertFalse(second.canVisit("order", "app-token"));
    }

    @Test
    void appliesTargetedAuthorizationAndBusinessChangesAtomically() {
        TinyIdTokenMapper mapper = mock(TinyIdTokenMapper.class);
        when(mapper.selectList(null)).thenReturn(List.of());
        TinyIdTokenServiceImpl service = service(mapper);
        service.refreshCache();

        service.replaceAuthorizations("app-token", List.of("order", "member"));
        assertTrue(service.canVisit("order", "app-token"));
        assertTrue(service.canVisit("member", "app-token"));

        service.removeBusiness("order");
        assertFalse(service.canVisit("order", "app-token"));
        assertTrue(service.canVisit("member", "app-token"));
    }

    /** 创建仅包含一个物理数据源的缓存服务。 */
    private TinyIdTokenServiceImpl service(TinyIdTokenMapper mapper) {
        DynamicDataSource routingDataSource = new DynamicDataSource();
        routingDataSource.setDataSourceKeys(List.of("datasource-0"));
        TinyIdInfoMapper infoMapper = mock(TinyIdInfoMapper.class);
        when(infoMapper.selectList(null)).thenReturn(List.of());
        return new TinyIdTokenServiceImpl(mapper, infoMapper, routingDataSource);
    }

    /** 创建测试授权实体。 */
    private TinyIdToken authorization(String token, String bizType) {
        TinyIdToken authorization = new TinyIdToken();
        authorization.setToken(token);
        authorization.setBizType(bizType);
        return authorization;
    }
}
