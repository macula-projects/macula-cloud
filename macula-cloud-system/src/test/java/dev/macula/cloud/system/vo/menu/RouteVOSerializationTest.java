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
package dev.macula.cloud.system.vo.menu;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证菜单路由及嵌套元数据可由 Redis 的 JDK 序列化器缓存。
 *
 * @author Rain
 * @since 2026-10-13
 */
class RouteVOSerializationTest {

    @Test
    void serializesNestedRoutesAndMetadata() {
        RouteVO parent = new RouteVO();
        parent.setId(1L);
        parent.setName("parent");
        RouteVO.Meta parentMeta = new RouteVO.Meta();
        parentMeta.setTitle("Parent");
        parentMeta.setRoles(List.of("ROLE_ADMIN"));
        parent.setMeta(parentMeta);

        RouteVO child = new RouteVO();
        child.setId(2L);
        child.setName("child");
        RouteVO.Meta childMeta = new RouteVO.Meta();
        childMeta.setTitle("Child");
        child.setMeta(childMeta);
        parent.setChildren(List.of(child));

        JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();
        byte[] bytes = serializer.serialize(new ArrayList<>(List.of(parent)));
        assertNotNull(bytes);

        @SuppressWarnings("unchecked")
        List<RouteVO> restored = (List<RouteVO>) serializer.deserialize(bytes);
        assertNotNull(restored);
        assertEquals("Parent", restored.get(0).getMeta().getTitle());
        assertEquals("Child", restored.get(0).getChildren().get(0).getMeta().getTitle());
    }
}
