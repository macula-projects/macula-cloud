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

package dev.macula.cloud.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code SysOAuth2ClientMapper} 客户端Mapper
 *
 * @author rain
 * @since 2023/4/11 19:41
 */
@Mapper
public interface SysOAuth2ClientMapper extends BaseMapper<SysOAuth2Client> {

}
