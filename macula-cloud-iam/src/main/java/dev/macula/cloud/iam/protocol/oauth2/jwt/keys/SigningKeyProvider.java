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
package dev.macula.cloud.iam.protocol.oauth2.jwt.keys;

import com.nimbusds.jose.jwk.RSAKey;

/**
 * IAM签名密钥来源端口；协议装配不依赖JKS或资源位置。
 *
 * @author Macula
 * @since 6.1.0
 */
public interface SigningKeyProvider {
    /**
     * 加载受信任的RSA签名材料，不能从认证请求选择位置或密钥。
     *
     * @return 包含签名私钥的RSA JWK
     * @throws IllegalStateException 密钥缺失、不可读取或不符合来源约束
     */
    RSAKey loadSigningKey();
}
