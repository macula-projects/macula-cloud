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

package org.springframework.security.config.annotation.web.builders;

import jakarta.servlet.Filter;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * 为由 {@link HttpSecurity#addFilter(Filter)} 延迟添加的自定义过滤器注册顺序。
 *
 * <p>该适配器与 Spring Security 的内部 {@link FilterOrderRegistration} 位于同一包，
 * 因而可以调用其包可见的 {@code put} 方法；仅通过反射读取 {@link HttpSecurity} 私有持有的注册表实例。</p>
 *
 * @author rain
 * @since 6.1.0
 */
public final class FilterOrderRegUtils {

    private static final Field FILTER_ORDERS_FIELD = findFilterOrdersField();

    private FilterOrderRegUtils() {
    }

    /**
     * 注册相对参考过滤器的顺序，但不把过滤器实例加入过滤器链。
     *
     * @param http            HttpSecurity 构建器
     * @param filter          待注册顺序的过滤器
     * @param referenceFilter 已注册顺序的参考过滤器类型
     * @param position        位于参考过滤器之前或之后
     */
    public static void registryFilterOrder(HttpSecurity http, Filter filter,
        Class<? extends Filter> referenceFilter, Position position) {
        Assert.notNull(http, "http cannot be null");
        Assert.notNull(filter, "filter cannot be null");
        Assert.notNull(referenceFilter, "referenceFilter cannot be null");
        Assert.notNull(position, "position cannot be null");

        FilterOrderRegistration filterOrders = getFilterOrders(http);
        Integer referenceFilterOrder = filterOrders.getOrder(referenceFilter);
        Assert.notNull(referenceFilterOrder,
            () -> "The Filter class " + referenceFilter.getName() + " does not have a registered order");
        filterOrders.put(filter.getClass(), referenceFilterOrder + position.offset);
    }

    /**
     * 相对参考过滤器的位置。
     */
    public enum Position {
        BEFORE(-1), AFTER(1);

        private final int offset;

        Position(int offset) {
            this.offset = offset;
        }
    }

    private static Field findFilterOrdersField() {
        Field field = ReflectionUtils.findField(HttpSecurity.class, "filterOrders", FilterOrderRegistration.class);
        Assert.state(field != null, "HttpSecurity.filterOrders field is not available");
        ReflectionUtils.makeAccessible(field);
        return field;
    }

    private static FilterOrderRegistration getFilterOrders(HttpSecurity http) {
        Object filterOrders = ReflectionUtils.getField(FILTER_ORDERS_FIELD, http);
        Assert.state(filterOrders instanceof FilterOrderRegistration,
            "HttpSecurity.filterOrders is not a FilterOrderRegistration");
        return (FilterOrderRegistration)filterOrders;
    }
}
