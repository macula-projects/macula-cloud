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

package dev.macula.cloud.system.api;

import dev.macula.cloud.system.api.fallback.AbstractMenuFeignFallbackFactory;
import dev.macula.cloud.system.vo.menu.RouteVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * {@code MenuFeignClient} 菜单服务接口
 *
 * @author rain
 * @since 2023/2/22 10:29
 */
@FeignClient(value = "macula-cloud-system", contextId = "menuFeignClient", fallbackFactory = AbstractMenuFeignFallbackFactory.class)
public interface MenuFeignClient {
    @GetMapping("/api/v1/menus/routes")
    List<RouteVO> listRoutes();
}
