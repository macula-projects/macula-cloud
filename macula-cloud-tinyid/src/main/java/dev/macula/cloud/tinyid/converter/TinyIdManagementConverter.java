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
package dev.macula.cloud.tinyid.converter;

import com.baomidou.mybatisplus.core.metadata.IPage;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdApplicationBO;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdAuditLogBO;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdBusinessAggregateBO;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdBusinessBO;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdDataSourceBO;
import dev.macula.cloud.tinyid.pojo.vo.TinyIdApplicationVO;
import dev.macula.cloud.tinyid.pojo.vo.TinyIdAuditLogVO;
import dev.macula.cloud.tinyid.pojo.vo.TinyIdBusinessAggregateVO;
import dev.macula.cloud.tinyid.pojo.vo.TinyIdBusinessVO;
import dev.macula.cloud.tinyid.pojo.vo.TinyIdDataSourceVO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 将 TinyID 管理领域对象转换为服务层对外视图对象。
 *
 * @author Rain
 * @since 6.1.0
 */
@Mapper(componentModel = "spring")
public interface TinyIdManagementConverter {

    /**
     * 转换接入应用。
     *
     * @param source 接入应用业务对象
     * @return 接入应用视图对象
     */
    TinyIdApplicationVO toApplicationVO(TinyIdApplicationBO source);

    /**
     * 转换单库发号业务。
     *
     * @param source 单库发号业务对象
     * @return 单库发号业务视图对象
     */
    TinyIdBusinessVO toBusinessVO(TinyIdBusinessBO source);

    /**
     * 转换单库发号业务列表。
     *
     * @param sources 单库发号业务对象列表
     * @return 单库发号业务视图对象列表
     */
    List<TinyIdBusinessVO> toBusinessVOs(List<TinyIdBusinessBO> sources);

    /**
     * 转换跨数据源发号业务。
     *
     * @param source 跨数据源发号业务对象
     * @return 跨数据源发号业务视图对象
     */
    TinyIdBusinessAggregateVO toBusinessAggregateVO(TinyIdBusinessAggregateBO source);

    /**
     * 转换物理数据源状态列表。
     *
     * @param sources 物理数据源状态业务对象列表
     * @return 物理数据源状态视图对象列表
     */
    List<TinyIdDataSourceVO> toDataSourceVOs(List<TinyIdDataSourceBO> sources);

    /**
     * 转换管理审计日志。
     *
     * @param source 管理审计日志业务对象
     * @return 管理审计日志视图对象
     */
    TinyIdAuditLogVO toAuditLogVO(TinyIdAuditLogBO source);

    /**
     * 转换接入应用分页结果。
     *
     * @param source 接入应用分页业务对象
     * @return 接入应用分页视图对象
     */
    default IPage<TinyIdApplicationVO> toApplicationPage(IPage<TinyIdApplicationBO> source) {
        return source.convert(this::toApplicationVO);
    }

    /**
     * 转换发号业务分页结果。
     *
     * @param source 发号业务分页业务对象
     * @return 发号业务分页视图对象
     */
    default IPage<TinyIdBusinessAggregateVO> toBusinessPage(IPage<TinyIdBusinessAggregateBO> source) {
        return source.convert(this::toBusinessAggregateVO);
    }

    /**
     * 转换管理审计日志分页结果。
     *
     * @param source 管理审计日志分页业务对象
     * @return 管理审计日志分页视图对象
     */
    default IPage<TinyIdAuditLogVO> toAuditLogPage(IPage<TinyIdAuditLogBO> source) {
        return source.convert(this::toAuditLogVO);
    }
}
