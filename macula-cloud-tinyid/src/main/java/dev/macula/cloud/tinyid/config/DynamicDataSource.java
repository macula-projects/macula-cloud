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

package dev.macula.cloud.tinyid.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Randomly routes existing TinyID issuance calls across configured physical data sources.
 *
 * @author Rain
 * @since 6.1.0
 */
public class DynamicDataSource extends AbstractRoutingDataSource {

    /** 当前管理操作强制使用的数据源标识。 */
    private static final ThreadLocal<String> LOOKUP_CONTEXT = new ThreadLocal<>();
    /** 可供发号请求随机选择的物理数据源标识。 */
    private List<String> dataSourceKeys = List.of();

    /**
     * 为当前发号请求随机选择一个物理数据源。
     *
     * @return 被选中的数据源标识
     */
    @Override
    protected Object determineCurrentLookupKey() {
        String selected = LOOKUP_CONTEXT.get();
        if (selected != null) {
            return selected;
        }
        if (dataSourceKeys.size() == 1) {
            return dataSourceKeys.get(0);
        }
        return dataSourceKeys.get(ThreadLocalRandom.current().nextInt(dataSourceKeys.size()));
    }

    /**
     * 在指定物理数据源上执行管理查询并恢复原路由上下文。
     *
     * @param sequence 物理数据源列表下标
     * @param action 查询动作
     * @param <T> 查询结果类型
     * @return 查询结果
     */
    public <T> T execute(int sequence, Supplier<T> action) {
        String previous = LOOKUP_CONTEXT.get();
        LOOKUP_CONTEXT.set(dataSourceKey(sequence));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                LOOKUP_CONTEXT.remove();
            } else {
                LOOKUP_CONTEXT.set(previous);
            }
        }
    }

    /**
     * 在指定物理数据源上执行管理命令并恢复原路由上下文。
     *
     * @param sequence 物理数据源列表下标
     * @param action 命令动作
     */
    public void execute(int sequence, Runnable action) {
        execute(sequence, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 返回已配置的物理数据源数量。
     *
     * @return 物理数据源数量
     */
    public int size() {
        return dataSourceKeys.size();
    }

    /**
     * 校验下标并返回对应的内部路由键。
     *
     * @param sequence 物理数据源列表下标
     * @return 内部路由键
     */
    private String dataSourceKey(int sequence) {
        if (sequence < 0 || sequence >= dataSourceKeys.size()) {
            throw new IllegalArgumentException("Unknown TinyID datasource sequence: " + sequence);
        }
        return dataSourceKeys.get(sequence);
    }

    /**
     * 设置可供随机路由选择的数据源标识。
     *
     * @param dataSourceKeys 有序数据源标识
     */
    public void setDataSourceKeys(List<String> dataSourceKeys) {
        if (dataSourceKeys == null || dataSourceKeys.isEmpty()) {
            throw new IllegalArgumentException("TinyID datasource keys must not be empty");
        }
        this.dataSourceKeys = List.copyOf(dataSourceKeys);
    }

}
