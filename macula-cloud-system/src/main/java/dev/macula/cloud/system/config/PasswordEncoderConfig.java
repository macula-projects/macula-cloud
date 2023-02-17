/*
 * Copyright (c) 2022 Macula
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

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.MessageDigest;

/**
 * 密码编码器
 *
 * @author haoxr
 * @since 2022/10/21
 */
@Configuration
public class PasswordEncoderConfig {
    private final MessageDigestPasswordEncoder md5Encoder = new MessageDigestPasswordEncoder("MD5"){
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new CustomBCryptPasswordEncoder();
    }

    @Deprecated
    public class CustomBCryptPasswordEncoder extends BCryptPasswordEncoder{
        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            if(StringUtils.isBlank(rawPassword)){
                return false;
            }
            if(md5Encoder.matches(rawPassword, encodedPassword)){
                return true;
            }
            return StringUtils.equals(rawPassword, encodedPassword) || super.matches(rawPassword, encodedPassword);
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return md5Encoder.encode(rawPassword);
        }
    }
}
