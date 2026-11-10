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
package dev.macula.cloud.iam.authentication.thirdparty;

import dev.macula.boot.starter.crypto.config.CryptoProperties;
import dev.macula.boot.starter.crypto.core.CryptoContext;
import dev.macula.boot.starter.crypto.core.CryptoManager;
import dev.macula.boot.starter.crypto.enums.AlgorithmType;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeSecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 复用平台加密器并为缓存信封增加用途/来源认证，拒绝明文与跨源替换。
 * @author Rain
 * @since 6.1.0
 */
@Component
public class ProviderCacheCipher {
    private static final String HMAC = "HmacSHA256";
    private final CryptoManager crypto;
    private final CryptoProperties properties;
    private final ExternalCodeSecurityProperties security;

    /** 不因未配置第三方来源而强制启用加密；实际使用时必须具备安全配置。 */
    @Autowired
    public ProviderCacheCipher(ObjectProvider<CryptoManager> crypto, ObjectProvider<CryptoProperties> properties,
        ExternalCodeSecurityProperties security) {
        this(crypto.getIfAvailable(), properties.getIfAvailable(), security);
    }

    /** 测试及受控装配使用同一加密边界。 */
    public ProviderCacheCipher(CryptoManager crypto, CryptoProperties properties, ExternalCodeSecurityProperties security) {
        this.crypto = crypto;
        this.properties = properties;
        this.security = security;
    }

    /** 加密后认证信封，不输出原值或密文到日志。 */
    public String encrypt(String context, String value) {
        requireEncryption();
        String ciphertext = crypto.encrypt(value, new CryptoContext(properties));
        return digest("envelope\0" + context + "\0" + ciphertext) + ":" + ciphertext;
    }

    /** 先验证用途和密文MAC再解密；损坏不得当作缓存未命中放宽。 */
    public String decrypt(String context, String envelope) {
        requireEncryption();
        int separator = envelope == null ? -1 : envelope.indexOf(':');
        if (separator != 64) { throw invalid(); }
        String ciphertext = envelope.substring(separator + 1);
        byte[] expected = digest("envelope\0" + context + "\0" + ciphertext).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, envelope.substring(0, separator).getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }
        try { return crypto.decrypt(ciphertext, new CryptoContext(properties)); }
        catch (RuntimeException ex) { throw invalid(); }
    }

    /** 服务端密钥摘要避免缓存键暴露主体、应用凭据或Code。 */
    public String digest(String value) {
        if (security.getDigestKey() == null || security.getDigestKey().isBlank()) { throw invalid(); }
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(security.getDigestKey().getBytes(StandardCharsets.UTF_8), HMAC));
            return HexFormat.of().formatHex(mac.doFinal(("provider-cache-v1\0" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) { throw invalid(); }
    }

    /** DEFAULT/BASE64不能提供缓存保密性，也不允许缺少真实加密Bean。 */
    private void requireEncryption() {
        if (crypto == null || properties == null || properties.getAlgorithm() == AlgorithmType.BASE64
            || properties.getAlgorithm() == AlgorithmType.DEFAULT) { throw invalid(); }
    }

    /** 不附带秘密、密文或密码学异常正文。 */
    private IllegalStateException invalid() { return new IllegalStateException("平台凭据缓存不可用"); }
}
