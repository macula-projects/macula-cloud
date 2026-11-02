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
package dev.macula.cloud.iam.protocol.oauth2.grant.externalcode;

import dev.macula.cloud.iam.utils.OAuth2ErrorCodesExpand;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * 表示外部身份已核验但尚未绑定本地账号的受控协议结果。
 *
 * @author Rain
 * @since 6.1.0
 */
public class ExternalBindingRequiredException extends OAuth2AuthenticationException {
    private final String bindingTicket;

    public ExternalBindingRequiredException(String bindingTicket) {
        super(new OAuth2Error(OAuth2ErrorCodesExpand.BINDING_REQUIRED,
            "The verified identity must be bound to an existing account.", null));
        this.bindingTicket = bindingTicket;
    }

    public String getBindingTicket() {
        return bindingTicket;
    }
}
