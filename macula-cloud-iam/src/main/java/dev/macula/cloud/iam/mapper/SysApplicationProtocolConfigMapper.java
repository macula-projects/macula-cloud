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
package dev.macula.cloud.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.macula.cloud.iam.pojo.entity.SysApplicationProtocolConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 按应用内部ID读取独立协议配置，不以OAuth Client ID限制未来协议。
 *
 * @author Rain
 * @since 6.1.0
 */
@Mapper
public interface SysApplicationProtocolConfigMapper extends BaseMapper<SysApplicationProtocolConfig> {
}
