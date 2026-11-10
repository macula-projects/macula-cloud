
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

// 此文件非必要，在生产环境下此文件配置可覆盖运行配置，开发环境下不起效
// 详情见 src/config/index.js

// 部署入口注入 OAUTH_CLIENT_ID/OAUTH_AUTHORITY/ADMIN_PUBLIC_URL/OAUTH_SCOPE。
const APP_CONFIG = {}
globalThis.APP_CONFIG = APP_CONFIG
