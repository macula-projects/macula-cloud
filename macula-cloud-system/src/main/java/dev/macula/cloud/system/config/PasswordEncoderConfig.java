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

package dev.macula.cloud.system.config;

import cn.hutool.crypto.SmUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.Assert;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 密码编码器
 *
 * @author haoxr
 * @since 2022/10/21
 */
@Configuration
public class PasswordEncoderConfig {
    private final String encodingId = "bcrypt";

    private final MessageDigestPasswordEncoder md5Encoder = new MessageDigestPasswordEncoder("MD5") {
        @Override
        public String encode(CharSequence rawPassword) {
            return md5(rawPassword);
        }

        /**
         * 二行制转字符串
         */
        private String byte2hex(byte[] b) {
            StringBuffer hs = new StringBuffer();
            String stmp = "";
            for (int n = 0; n < b.length; n++) {
                stmp = (java.lang.Integer.toHexString(b[n] & 0XFF));
                if (stmp.length() == 1) {
                    hs.append("0").append(stmp);
                } else {
                    hs.append(stmp);
                }
            }
            return StringUtils.lowerCase(hs.toString());
        }

        /**
         * 生产字符串的Md5值
         * @param context
         */
        public String md5(CharSequence context) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                return byte2hex(md.digest(context.toString().getBytes("UTF-8")));
            } catch (Exception e) {
                throw new java.lang.RuntimeException("md5 error!", e);
            }
        }
    };

    private final PasswordEncoder sm3PasswordEncoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return SmUtil.sm3(rawPassword.toString());
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            if (rawPassword == null) {
                throw new IllegalArgumentException("rawPassword cannot be null");
            }
            if (encodedPassword == null || encodedPassword.length() == 0) {
                return false;
            }
            String rawPasswordEncoded = this.encode(rawPassword.toString());
            return rawPasswordEncoded.equals(encodedPassword);
        }
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        encoders.put("ldap", new org.springframework.security.crypto.password.LdapShaPasswordEncoder());
        encoders.put("MD4", new org.springframework.security.crypto.password.Md4PasswordEncoder());
        encoders.put("MD5", new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("MD5"));
        encoders.put("noop", org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance());
        encoders.put("pbkdf2", new org.springframework.security.crypto.password.Pbkdf2PasswordEncoder());
        encoders.put("scrypt", new org.springframework.security.crypto.scrypt.SCryptPasswordEncoder());
        encoders.put("SHA-1", new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("SHA-1"));
        encoders.put("SHA-256",
            new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("SHA-256"));
        encoders.put("sha256", new org.springframework.security.crypto.password.StandardPasswordEncoder());
        encoders.put("argon2", new org.springframework.security.crypto.argon2.Argon2PasswordEncoder());
        encoders.put("SM3", sm3PasswordEncoder);

        Assert.isTrue(encoders.containsKey(encodingId), encodingId + " is not found in idToPasswordEncoder");

        // 密码默认加密方法是bcrypt
        DelegatingPasswordEncoder delegatingPasswordEncoder = new DelegatingPasswordEncoder(encodingId, encoders);

        // 默认匹配是纯md5
        delegatingPasswordEncoder.setDefaultPasswordEncoderForMatches(md5Encoder);

        return delegatingPasswordEncoder;
    }
}
