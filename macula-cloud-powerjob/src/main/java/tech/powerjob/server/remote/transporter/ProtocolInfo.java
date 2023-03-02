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

package tech.powerjob.server.remote.transporter;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import tech.powerjob.remote.framework.transporter.Transporter;

/**
 * ProtocolInfo
 *
 * @author tjq
 * @since 2023/1/21
 */
@Getter
@Setter
@ToString
public class ProtocolInfo {

    private String protocol;

    private String address;

    private transient Transporter transporter;

    public ProtocolInfo(String protocol, String address, Transporter transporter) {
        this.protocol = protocol;
        this.address = address;
        this.transporter = transporter;
    }
}
