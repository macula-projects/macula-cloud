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

package dev.macula.cloud.iam.service.oauth2;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import dev.macula.cloud.iam.authentication.captcha.CaptchaAuthenticationToken;
import dev.macula.cloud.iam.authentication.weapp.WeappAuthenticationToken;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.iam.jackson.MaculaIamJacksonModule;
import dev.macula.cloud.iam.pojo.dto.Authorization;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.jspecify.annotations.Nullable;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.HashMap;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.oauth2.core.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * {@code MaculaOAuth2AuthorizationService} TOKEN缓存服务
 *
 * @author rain
 * @since 2023/4/11 08:14
 */
public class MaculaOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private static final String VERSION = "macula.authorization.version";
    private static final String STATE_EXPIRY = "macula.authorization.stateExpiresAt";
    private static final Map<String, Class<? extends OAuth2Token>> TOKEN_TYPES = new LinkedHashMap<>();
    static {
        TOKEN_TYPES.put("access_token", OAuth2AccessToken.class);
        TOKEN_TYPES.put("refresh_token", OAuth2RefreshToken.class);
        TOKEN_TYPES.put("code", OAuth2AuthorizationCode.class);
        TOKEN_TYPES.put("id_token", OidcIdToken.class);
        TOKEN_TYPES.put("device_code", OAuth2DeviceCode.class);
        TOKEN_TYPES.put("user_code", OAuth2UserCode.class);
        TOKEN_TYPES.put("state", null);
    }
    // All v2 keys share a slot. CAS and index replacement are a single Redis operation.
    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
        local current = redis.call('GET', KEYS[1])
        local version = 0
        if current then version = cjson.decode(current).version end
        if version ~= tonumber(ARGV[1]) then return 0 end
        local oldCount = tonumber(ARGV[4])
        for i = 2, oldCount + 1 do redis.call('DEL', KEYS[i]) end
        redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
        for i = oldCount + 2, #KEYS do
            redis.call('SET', KEYS[i], ARGV[5], 'PX', ARGV[i - oldCount + 4])
        end
        return 1
        """, Long.class);
    private final StringRedisTemplate strings;
    private final ObjectMapper recordMapper = JsonMapper.builder().build();
    private static final String AUTHORIZATION = CacheConstants.OAUTH2_TOKEN_KEY;

    private final RedisTemplate<String, Object> redisTemplate;

    private final RegisteredClientRepository registeredClientRepository;
    private final ObjectMapper objectMapper;

    public MaculaOAuth2AuthorizationService(RedisTemplate<String, Object> redisTemplate,
        RegisteredClientRepository registeredClientRepository) {

        this.registeredClientRepository = registeredClientRepository;
        this.redisTemplate = redisTemplate;
        this.strings = new StringRedisTemplate(Objects.requireNonNull(redisTemplate.getConnectionFactory()));
        ClassLoader classLoader = MaculaOAuth2AuthorizationService.class.getClassLoader();
        var allowedTypes = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType(SysUserDetails.class)
            .allowIfSubType(CaptchaAuthenticationToken.class)
            .allowIfSubType(WeappAuthenticationToken.class)
            .allowIfSubType(Long.class);
        // Includes OAuth2AuthorizationServerJacksonModule and its constrained type validator.
        this.objectMapper = JsonMapper.builder()
            .addModules(SecurityJacksonModules.getModules(classLoader, allowedTypes))
            .addModule(new MaculaIamJacksonModule())
            .build();
    }

    private static AuthorizationGrantType resolveAuthorizationGrantType(String authorizationGrantType) {
        if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.AUTHORIZATION_CODE;
        } else if (AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.CLIENT_CREDENTIALS;
        } else if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.REFRESH_TOKEN;
        }
        // Custom authorization grant type
        return new AuthorizationGrantType(authorizationGrantType);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        Authorization current = readRecord(authorization.getId());
        if (current != null && current.isDeleted()) return;
        Authorization previous = current != null ? current : toEntity(authorization);
        Authorization deleted = toEntity(authorization);
        deleted.setDeleted(true);
        deleted.setVersion(previous.getVersion() + 1);
        deleted.setRetentionExpiresAt(retention(previous));
        persist(previous, deleted, Map.of(), previous.getVersion());
    }

    @Override
    @Nullable
    public OAuth2Authorization findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        Authorization record = readRecord(id);
        return unavailable(record) ? null : toObject(record);
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        if (dev.macula.cloud.iam.playground.PlaygroundRegisteredClientRepository.isPlayground(authorization.getRegisteredClientId())
            && registeredClientRepository.findById(authorization.getRegisteredClientId()) == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        Authorization previous = readRecord(authorization.getId());
        Number expected = authorization.getAttribute(VERSION);
        long version = expected == null ? 0 : expected.longValue();
        Authorization record = toEntity(authorization);
        record.setVersion(version + 1);
        if (record.getState() != null) {
            Long expiry = authorization.getAttribute(STATE_EXPIRY);
            record.setStateExpiresAt(previous != null && previous.getStateExpiresAt() != null
                ? previous.getStateExpiresAt()
                : expiry == null ? Instant.now().plusSeconds(600) : Instant.ofEpochMilli(expiry));
        }
        Instant expires = retention(record);
        if (previous != null && retention(previous).isAfter(expires)) expires = retention(previous);
        record.setRetentionExpiresAt(expires);
        persist(previous, record, indexes(record), version);
    }

    private void persist(Authorization previous, Authorization record, Map<String, Instant> next, long version) {
        List<String> keys = new ArrayList<>();
        keys.add(recordKey(record.getId()));
        // Keep expired old keys in the delete set as well.
        if (previous != null) keys.addAll(indexes(previous).keySet());
        int oldCount = keys.size() - 1;
        List<String> args = new ArrayList<>(List.of(Long.toString(version), writeRecord(record),
            Long.toString(Math.max(1, Duration.between(Instant.now(), retention(record)).toMillis())),
            Integer.toString(oldCount), record.getId()));
        Instant now = Instant.now();
        next.forEach((key, expiry) -> {
            long ttl = Duration.between(now, expiry).toMillis();
            if (ttl > 0) { keys.add(key); args.add(Long.toString(ttl)); }
        });
        Long saved = strings.execute(SAVE_SCRIPT, keys, args.toArray());
        if (!Long.valueOf(1).equals(saved)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                "Authorization was concurrently modified; restart the authorization flow.", null));
        }
    }

    private Map<String, Instant> indexes(Authorization record) {
        Map<String, Instant> result = new LinkedHashMap<>();
        addIndex(result, "access_token", record.getAccessTokenValue(), record.getAccessTokenExpiresAt());
        addIndex(result, "refresh_token", record.getRefreshTokenValue(), record.getRefreshTokenExpiresAt());
        addIndex(result, "code", record.getAuthorizationCodeValue(), record.getAuthorizationCodeExpiresAt());
        // RP-Initiated Logout accepts an expired ID Token while the authorization/session is retained.
        addIndex(result, "id_token", record.getOidcIdTokenValue(), retention(record));
        addIndex(result, "device_code", record.getDeviceCodeValue(), record.getDeviceCodeExpiresAt());
        addIndex(result, "user_code", record.getUserCodeValue(), record.getUserCodeExpiresAt());
        addIndex(result, "state", record.getState(), record.getStateExpiresAt());
        return result;
    }

    private void addIndex(Map<String, Instant> indexes, String type, String value, Instant expiry) {
        if (value != null && expiry != null) indexes.put(indexKey(type, value), expiry);
    }

    private Instant retention(Authorization record) {
        Instant latest = record.getRetentionExpiresAt() == null ? Instant.now() : record.getRetentionExpiresAt();
        for (Instant expiry : new Instant[] {record.getStateExpiresAt(), record.getAuthorizationCodeExpiresAt(),
            record.getAccessTokenExpiresAt(), record.getRefreshTokenExpiresAt(), record.getOidcIdTokenExpiresAt(),
            record.getDeviceCodeExpiresAt(), record.getUserCodeExpiresAt()}) {
            if (expiry != null && expiry.isAfter(latest)) latest = expiry;
        }
        return latest;
    }

    @Override
    @Nullable
    public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");
        if (tokenType != null) return TOKEN_TYPES.containsKey(tokenType.getValue())
            ? findByType(token, tokenType.getValue()) : null;
        for (String type : TOKEN_TYPES.keySet()) {
            OAuth2Authorization result = findByType(token, type);
            if (result != null) return result;
        }
        return null;
    }

    private OAuth2Authorization findByType(String value, String type) {
        String id = strings.opsForValue().get(indexKey(type, value));
        Authorization record;
        if (id != null) {
            record = readRecord(id);
        } else {
            Object legacy = redisTemplate.opsForValue().get(String.format("%s:%s:%s", AUTHORIZATION, type, value));
            if (legacy == null) return null;
            if (!(legacy instanceof Authorization)) throw new IllegalStateException("Unsupported authorization cache format");
            Authorization old = (Authorization) legacy;
            record = readRecord(old.getId());
            if (record == null) {
                record = old;
                if ("state".equals(type)) {
                    Long ttl = redisTemplate.getExpire(String.format("%s:%s:%s", AUTHORIZATION, type, value),
                        TimeUnit.MILLISECONDS);
                    if (ttl == null || ttl <= 0) return null;
                    record.setStateExpiresAt(Instant.now().plusMillis(Math.min(ttl, 600_000)));
                }
            }
        }
        if (unavailable(record)) return null;
        OAuth2Authorization result = toObject(record);
        if ("state".equals(type)) return value.equals(record.getState()) && record.getStateExpiresAt() != null
            && record.getStateExpiresAt().isAfter(Instant.now()) ? result : null;
        OAuth2Authorization.Token<?> found = result.getToken(TOKEN_TYPES.get(type));
        // Preserve invalidation metadata for the protocol provider; don't resurrect replaced tokens.
        return found != null && value.equals(found.getToken().getTokenValue())
            && ("id_token".equals(type) || !found.isExpired()) ? result : null;
    }

    private Authorization readRecord(String id) {
        String data = strings.opsForValue().get(recordKey(id));
        if (data == null) return null;
        try { return recordMapper.readValue(data, Authorization.class); }
        catch (Exception ex) { throw new IllegalStateException("Cannot read authorization record", ex); }
    }

    private boolean unavailable(@Nullable Authorization record) {
        return record == null || record.isDeleted()
            || (dev.macula.cloud.iam.playground.PlaygroundRegisteredClientRepository.isPlayground(record.getRegisteredClientId())
                && registeredClientRepository.findById(record.getRegisteredClientId()) == null);
    }

    private String writeRecord(Authorization record) {
        try { return recordMapper.writeValueAsString(record); }
        catch (Exception ex) { throw new IllegalStateException("Cannot write authorization record", ex); }
    }

    private String recordKey(String id) { return AUTHORIZATION + ":{iam-oauth2}:v2:authorization:" + digest(id); }
    private String indexKey(String type, String value) {
        return AUTHORIZATION + ":{iam-oauth2}:v2:" + type + ":" + digest(value);
    }
    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private OAuth2Authorization toObject(Authorization entity) {
        Assert.notNull(entity, "authorization cannot be null");

        RegisteredClient registeredClient =
            this.registeredClientRepository.findById(entity.getRegisteredClientId());
        if (registeredClient == null) {
            throw new DataRetrievalFailureException(
                "The RegisteredClient with id '" + entity.getRegisteredClientId() + "' was not found in the RegisteredClientRepository.");
        }

        OAuth2Authorization.Builder builder =
            OAuth2Authorization.withRegisteredClient(registeredClient).id(entity.getId())
                .principalName(entity.getPrincipalName())
                .authorizationGrantType(resolveAuthorizationGrantType(entity.getAuthorizationGrantType()))
                .attributes(attributes -> {
                    attributes.putAll(parseMap(entity.getAttributes()));
                    attributes.put(VERSION, entity.getVersion());
                    if (entity.getStateExpiresAt() != null)
                        attributes.put(STATE_EXPIRY, entity.getStateExpiresAt().toEpochMilli());
                });
        if (entity.getState() != null) {
            builder.attribute(OAuth2ParameterNames.STATE, entity.getState());
        }

        if (entity.getAuthorizedScopes() != null) {
            builder.authorizedScopes(StringUtils.commaDelimitedListToSet(entity.getAuthorizedScopes()));
        }

        if (entity.getAuthorizationCodeValue() != null) {
            OAuth2AuthorizationCode authorizationCode =
                new OAuth2AuthorizationCode(entity.getAuthorizationCodeValue(), entity.getAuthorizationCodeIssuedAt(),
                    entity.getAuthorizationCodeExpiresAt());
            builder.token(authorizationCode,
                metadata -> metadata.putAll(parseMap(entity.getAuthorizationCodeMetadata())));
        }

        if (entity.getAccessTokenValue() != null) {
            OAuth2AccessToken accessToken =
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, entity.getAccessTokenValue(),
                    entity.getAccessTokenIssuedAt(), entity.getAccessTokenExpiresAt(),
                    StringUtils.commaDelimitedListToSet(entity.getAccessTokenScopes()));
            builder.token(accessToken, metadata -> metadata.putAll(parseMap(entity.getAccessTokenMetadata())));
        }

        if (entity.getRefreshTokenValue() != null) {
            OAuth2RefreshToken refreshToken =
                new OAuth2RefreshToken(entity.getRefreshTokenValue(), entity.getRefreshTokenIssuedAt(),
                    entity.getRefreshTokenExpiresAt());
            builder.token(refreshToken, metadata -> metadata.putAll(parseMap(entity.getRefreshTokenMetadata())));
        }

        if (entity.getOidcIdTokenValue() != null) {
            OidcIdToken idToken = new OidcIdToken(entity.getOidcIdTokenValue(), entity.getOidcIdTokenIssuedAt(),
                entity.getOidcIdTokenExpiresAt(), parseMap(entity.getOidcIdTokenClaims()));
            builder.token(idToken, metadata -> metadata.putAll(parseMap(entity.getOidcIdTokenMetadata())));
        }

        if (entity.getDeviceCodeValue() != null) {
            builder.token(new OAuth2DeviceCode(entity.getDeviceCodeValue(), entity.getDeviceCodeIssuedAt(),
                entity.getDeviceCodeExpiresAt()), metadata -> metadata.putAll(parseMap(entity.getDeviceCodeMetadata())));
        }
        if (entity.getUserCodeValue() != null) {
            builder.token(new OAuth2UserCode(entity.getUserCodeValue(), entity.getUserCodeIssuedAt(),
                entity.getUserCodeExpiresAt()), metadata -> metadata.putAll(parseMap(entity.getUserCodeMetadata())));
        }
        return builder.build();
    }

    private Authorization toEntity(OAuth2Authorization authorization) {
        Authorization entity = new Authorization();
        entity.setId(authorization.getId());
        entity.setRegisteredClientId(authorization.getRegisteredClientId());
        entity.setPrincipalName(authorization.getPrincipalName());
        entity.setAuthorizationGrantType(authorization.getAuthorizationGrantType().getValue());
        if (!CollectionUtils.isEmpty(authorization.getAuthorizedScopes())) {
            entity.setAuthorizedScopes(
                StringUtils.collectionToDelimitedString(authorization.getAuthorizedScopes(), ","));
        }
        Map<String, Object> attributes = new HashMap<>(authorization.getAttributes());
        attributes.remove(VERSION);
        attributes.remove(STATE_EXPIRY);
        entity.setAttributes(writeMap(attributes));
        entity.setState(authorization.getAttribute(OAuth2ParameterNames.STATE));

        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode =
            authorization.getToken(OAuth2AuthorizationCode.class);
        setTokenValues(authorizationCode, entity::setAuthorizationCodeValue, entity::setAuthorizationCodeIssuedAt,
            entity::setAuthorizationCodeExpiresAt, entity::setAuthorizationCodeMetadata);

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getToken(OAuth2AccessToken.class);
        setTokenValues(accessToken, entity::setAccessTokenValue, entity::setAccessTokenIssuedAt,
            entity::setAccessTokenExpiresAt, entity::setAccessTokenMetadata);
        if (accessToken != null && !CollectionUtils.isEmpty(accessToken.getToken().getScopes())) {
            entity.setAccessTokenScopes(
                StringUtils.collectionToDelimitedString(accessToken.getToken().getScopes(), ","));
        }

        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getToken(OAuth2RefreshToken.class);
        setTokenValues(refreshToken, entity::setRefreshTokenValue, entity::setRefreshTokenIssuedAt,
            entity::setRefreshTokenExpiresAt, entity::setRefreshTokenMetadata);

        OAuth2Authorization.Token<OidcIdToken> oidcIdToken = authorization.getToken(OidcIdToken.class);
        setTokenValues(oidcIdToken, entity::setOidcIdTokenValue, entity::setOidcIdTokenIssuedAt,
            entity::setOidcIdTokenExpiresAt, entity::setOidcIdTokenMetadata);
        if (oidcIdToken != null) {
            entity.setOidcIdTokenClaims(writeMap(oidcIdToken.getToken().getClaims()));
        }

        setTokenValues(authorization.getToken(OAuth2DeviceCode.class), entity::setDeviceCodeValue,
            entity::setDeviceCodeIssuedAt, entity::setDeviceCodeExpiresAt, entity::setDeviceCodeMetadata);
        setTokenValues(authorization.getToken(OAuth2UserCode.class), entity::setUserCodeValue,
            entity::setUserCodeIssuedAt, entity::setUserCodeExpiresAt, entity::setUserCodeMetadata);
        return entity;
    }

    private void setTokenValues(OAuth2Authorization.Token<?> token, Consumer<String> tokenValueConsumer,
        Consumer<Instant> issuedAtConsumer, Consumer<Instant> expiresAtConsumer, Consumer<String> metadataConsumer) {
        if (token != null) {
            OAuth2Token oAuth2Token = token.getToken();
            tokenValueConsumer.accept(oAuth2Token.getTokenValue());
            issuedAtConsumer.accept(oAuth2Token.getIssuedAt());
            expiresAtConsumer.accept(oAuth2Token.getExpiresAt());
            metadataConsumer.accept(writeMap(token.getMetadata()));
        }
    }

    private Map<String, Object> parseMap(String data) {
        try {
            return this.objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private String writeMap(Map<String, Object> metadata) {
        try {
            return this.objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }
}
