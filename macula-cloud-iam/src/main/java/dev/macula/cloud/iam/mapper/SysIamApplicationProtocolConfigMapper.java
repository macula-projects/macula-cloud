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
import dev.macula.cloud.iam.pojo.entity.SysIamApplicationProtocolConfig;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 应用的唯一协议登记；秘密字段不参与普通JSON或日志投影。
 * @author Rain
 * @since 6.1.0
 */
@Mapper
public interface SysIamApplicationProtocolConfigMapper extends BaseMapper<SysIamApplicationProtocolConfig> {
    /** 依赖application_id唯一约束读取唯一登记，不接受任意默认协议。 */
    @Select("SELECT * FROM sys_iam_application_protocol_config WHERE application_id=#{applicationId}")
    SysIamApplicationProtocolConfig selectByApplicationId(@Param("applicationId") Long applicationId);

    /** 在持有主应用锁的同一事务中锁定登记，避免RR快照读到旧配置。 */
    @Select("SELECT * FROM sys_iam_application_protocol_config WHERE application_id=#{applicationId} FOR UPDATE")
    SysIamApplicationProtocolConfig selectByApplicationIdForUpdate(@Param("applicationId") Long applicationId);
    /** OAuth家族沿用生成列的旧大小写/唯一语义，未分配槽不提供认证。 */
    @Select("SELECT * FROM sys_iam_application_protocol_config WHERE oauth2_identifier=#{identifier} AND protocol IN ('OIDC','OAUTH2_1') AND schema_version>0")
    SysIamApplicationProtocolConfig selectByOAuth2Identifier(@Param("identifier") String identifier);

    /** 只将服务器持有的旧Client引用解析为内部ID，不读取或返回凭据。 */
    @Select("SELECT application_id FROM sys_iam_application_protocol_config WHERE oauth2_identifier=#{identifier} AND protocol IN ('OIDC','OAUTH2_1') AND schema_version>0")
    Long selectApplicationIdByOAuth2Identifier(@Param("identifier") String identifier);

    /** 必须先持有主应用锁再调用，防止锁序与配置写入相反。 */
    @Select("SELECT * FROM sys_iam_application_protocol_config WHERE oauth2_identifier=#{identifier} AND protocol IN ('OIDC','OAUTH2_1') AND schema_version>0 FOR UPDATE")
    SysIamApplicationProtocolConfig selectByOAuth2IdentifierForUpdate(@Param("identifier") String identifier);

    /** Origin索引只投影启用业务应用，不将资源管理或草稿作为浏览器入口。 */
    @Select("SELECT p.protocol_identifier FROM sys_iam_application_protocol_config p JOIN sys_iam_application a ON a.id=p.application_id WHERE p.enabled=1 AND a.configuration_status='ENABLED' AND a.purpose='APPLICATION' AND p.protocol IN ('OIDC','OAUTH2_1') AND p.schema_version>0 ORDER BY p.application_id")
    List<String> selectEnabledOAuth2Identifiers();
}
