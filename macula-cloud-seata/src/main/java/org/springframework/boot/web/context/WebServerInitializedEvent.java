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

package org.springframework.boot.web.context;

import org.springframework.boot.web.server.WebServer;

/**
 * Preserves the Spring Boot 3 event type referenced by Seata 2.0's server runner.
 *
 * @author Rain
 * @since 6.1.0
 */
public abstract class WebServerInitializedEvent
    extends org.springframework.boot.web.server.context.WebServerInitializedEvent {

    protected WebServerInitializedEvent(WebServer webServer) {
        super(webServer);
    }
}
