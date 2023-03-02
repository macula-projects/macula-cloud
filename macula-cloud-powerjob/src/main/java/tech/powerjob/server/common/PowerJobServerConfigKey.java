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

package tech.powerjob.server.common;

/**
 * 配置文件 key
 *
 * @author tjq
 * @since 2020/8/2
 */
public class PowerJobServerConfigKey {

    /**
     * akka 协议端口号
     */
    public static final String AKKA_PORT = "oms.akka.port";
    /**
     * http 协议端口号
     */
    public static final String HTTP_PORT = "oms.http.port";
    /**
     * 自定义数据库表前缀
     */
    public static final String TABLE_PREFIX = "oms.table-prefix";
    /**
     * 是否使用 mongoDB
     */
    public static final String MONGODB_ENABLE = "oms.mongodb.enable";
    /**
     * 是否启用 Swagger-UI，默认关闭
     */
    public static final String SWAGGER_UI_ENABLE = "oms.swagger.enable";

    /**
     * 钉钉报警相关
     */
    public static final String DING_APP_KEY = "oms.alarm.ding.app-key";
    public static final String DING_APP_SECRET = "oms.alarm.ding.app-secret";
    public static final String DING_AGENT_ID = "oms.alarm.ding.agent-id";
}
