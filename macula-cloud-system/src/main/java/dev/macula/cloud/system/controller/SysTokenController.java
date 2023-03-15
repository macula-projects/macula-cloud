package dev.macula.cloud.system.controller;

import com.alibaba.fastjson.JSONObject;
import dev.macula.boot.constants.SecurityConstants;
import dev.macula.boot.result.ApiResultCode;
import dev.macula.boot.result.Result;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.boot.starter.web.annotation.NotControllerResponseAdvice;
import dev.macula.cloud.system.dto.UserAuthInfo;
import dev.macula.cloud.system.service.SysMenuService;
import dev.macula.cloud.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录token的控制器 临时处理类
 *
 * @author qiuyuhao
 * @date 2023.01.16
 */
@Tag(name = "登录接口", description = "登录接口")
@RequestMapping("/api/token")
@RestController
@Slf4j
@Deprecated
@RequiredArgsConstructor
@RefreshScope
public class SysTokenController {
    private static final Map<String, UserAuthInfo> USER_TOKEN_MAP = new ConcurrentHashMap<>();
    private static final HttpHeaders DEFAULT_HEADERS;
    private final SysUserService userService;
    private final SysMenuService menuService;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate redisTemplate;

    @Value("${test.env}")
    private String testEnv;

    static {
        DEFAULT_HEADERS = new HttpHeaders();
        DEFAULT_HEADERS.set("Authorization", getBaseAuthHeader("client", "secret"));
    }

    @GetMapping("/testenv")
    public String test() {
        return testEnv;
    }

    @Operation(summary = "MaculaV5获取用户权限信息")
    @PostMapping("/introspect")
    @NotControllerResponseAdvice
    public JSONObject postIntrospect(@RequestParam("token") String token) {
        redisTemplate.opsForSet().members(SecurityConstants.SECURITY_USER_BTN_PERMS_KEY + "1");
        JSONObject jsonObject = new JSONObject();
        UserAuthInfo userAuthInfo = USER_TOKEN_MAP.get(token);
        if (Objects.isNull(userAuthInfo)) {
            jsonObject.put("active", false);
            return jsonObject;
        }
        jsonObject.put("active", true);
        jsonObject.put("exp", 359999);
        jsonObject.put("scope", "all");
        jsonObject.put("sub", userAuthInfo.getUsername());
        jsonObject.put("authorities", userAuthInfo.getRoles());
        log.error("introspect: {}", userAuthInfo);
        return jsonObject;
    }

    @Operation(summary = "MaculaV5获取用户登录token")
    @PostMapping
    public Result postMaculaV5Token(@RequestBody @Valid UserDto userDto) throws Exception {
        UserAuthInfo userAuthInfo = userService.getUserAuthInfo(userDto.getUsername());
        JSONObject jsonObject = new JSONObject();
        if (Objects.nonNull(userAuthInfo) && passwordEncoder.matches(userDto.getPassword(),
            userAuthInfo.getPassword())) {
            String token = Base64.encodeBase64String(
                (UUID.randomUUID().toString() + "##" + userDto.getUsername()).getBytes("UTF-8"));
            USER_TOKEN_MAP.put(token, userAuthInfo);
            jsonObject.put("access_token", token);
            jsonObject.put("token_type", "bearer");
            jsonObject.put("refresh_token", token);
            jsonObject.put("expires_in", 359999);
            jsonObject.put("scope", "all");
            jsonObject.put("real_username", userDto.getUsername());
            jsonObject.put("is_show_user_agreement", "false");
            redisTemplate.delete("gateway:jwt:" + userDto.getUsername());
            return Result.success(jsonObject);
        }
        jsonObject.put("msg", "用户名或密码错误");
        return Result.failed(ApiResultCode.FAILED, jsonObject);
    }

    @Operation(summary = "获取用户登录token")
    //@PostMapping
    @Deprecated
    public Result postToken(@RequestBody @Valid SysTokenController.UserDto userVo) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<String, String>();
        params.add("grant_type", "password");
        params.add("loginType", "password");
        params.add("terminalType", "PC");
        params.add("username", userVo.getUsername());
        params.add("password", userVo.getPassword());
        HttpEntity<MultiValueMap<String, String>> requestEntity =
            new HttpEntity<MultiValueMap<String, String>>(params, DEFAULT_HEADERS);
        ResponseEntity<JSONObject> responseEntity =
            getRestTemplate().exchange("https://goauth-dev.infinitus.com.cn/oauth/token", HttpMethod.POST,
                requestEntity, JSONObject.class);
        int code = responseEntity.getStatusCodeValue();
        if (code == 200) {
            return Result.success(responseEntity.getBody());
        }
        return Result.failed(ApiResultCode.FAILED, responseEntity);
    }

    @Operation(summary = "验证登录token是否有效")
    @GetMapping("/userInfo")
    public Result userInfo() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("userName", SecurityUtils.getCurrentUser());
        jsonObject.put("dashboard", 0);
        jsonObject.put("role", SecurityUtils.getRoles());
        // 后续要读redis，不过存的perm好像是字符串，后续验证，先直接访问services
        jsonObject.put("perm", menuService.listRolePerms(SecurityUtils.getRoles()));
        return Result.success(jsonObject);
    }

    private static String getBaseAuthHeader(String username, String password) {
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("US-ASCII")));
        return "Basic " + new String(encodedAuth);
    }

    @Data
    @Schema(description = "隐式登录用户账号信息对象")
    private static class UserDto implements Serializable {
        @Schema(description = "用户的密码")
        @NotNull(message = "密码不能为空")
        @Length(min = 1, message = "密码不能为空")
        private String password;
        @Schema(description = "用户的用户名")
        @NotNull(message = "用户名不能为空")
        @Length(min = 1, message = "用户名不能为空")
        private String username;
    }

    /**
     * 与oauthApi共用同一个缓存的restTemplate
     *
     * @return
     */
    public static RestTemplate getRestTemplate() {
        return RestTemplateHolder.INSTANCE;
    }

    private static class RestTemplateHolder {
        private static final RestTemplate INSTANCE = createRestTemplate();

        private static RestTemplate createRestTemplate() {
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            connectionManager.setMaxTotal(600);
            connectionManager.setDefaultMaxPerRoute(300);

            RequestConfig.Builder requestBuilder = RequestConfig.custom();
            requestBuilder.setConnectTimeout(10000);

            HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
            httpClientBuilder.setDefaultRequestConfig(requestBuilder.build());
            httpClientBuilder.setConnectionManager(connectionManager);

            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
            factory.setHttpClient(httpClientBuilder.build());

            RestTemplate template = new RestTemplate(factory);
            List<HttpMessageConverter<?>> messageConverters = template.getMessageConverters();
            if (null != messageConverters && !messageConverters.isEmpty()) {
                for (int i = 0; i < messageConverters.size(); i++) {
                    HttpMessageConverter<?> converter = messageConverters.get(i);
                    if (converter.getClass() == StringHttpMessageConverter.class) {
                        messageConverters.remove(converter);
                        messageConverters.add(i, new StringHttpMessageConverter(Charset.forName("UTF-8")));
                        break;
                    }
                }
            }
            return template;
        }
    }
}
