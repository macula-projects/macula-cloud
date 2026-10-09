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

package dev.macula.cloud.iam.playground;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import org.springframework.core.env.Environment;

/**
 * 对所有演示入口应用相同的环境门禁，生产 profile 拥有否决权。
 * @author Rain
 * @since 6.1.0
 */
public final class PlaygroundAccessPolicy {
    private final PlaygroundProperties properties;
    private final Environment environment;

    public PlaygroundAccessPolicy(PlaygroundProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public boolean isEnabled() {
        Set<String> active = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        return properties.isEnabled() && !active.contains("prd") && !active.contains("production")
            && properties.getAllowedProfiles() != null
            && active.stream().anyMatch(properties.getAllowedProfiles()::contains);
    }

    public URI issuer(String value) {
        URI uri = URI.create(value);
        boolean loopback = Set.of("localhost", "127.0.0.1", "[::1]").contains(
            uri.getHost() == null ? "" : uri.getHost());
        boolean local = Arrays.asList(environment.getActiveProfiles()).contains("local");
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
            || uri.getFragment() != null || uri.getPort() == 0
            || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                && properties.isAllowLoopbackHttp() && local && loopback))
            || (!uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Playground requires an HTTPS root issuer; local loopback HTTP needs explicit permission");
        }
        return URI.create(value.endsWith("/") ? value.substring(0, value.length() - 1) : value);
    }
}
