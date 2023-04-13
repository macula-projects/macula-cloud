package dev.macula.cloud.iam.authentication.weapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.macula.boot.result.Result;
import dev.macula.cloud.iam.handler.ResponseWriter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/**
 * 小程序预授权
 *
 * @author n1
 * @since 2021 /6/25 10:44
 */
public class WeappPreAuthenticationFilter extends OncePerRequestFilter {
    private static final String ENDPOINT = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String WEAPP_CLIENT_KEY = "clientId";
    private static final String JS_CODE_KEY = "jsCode";
    private static final String ATTRIBUTE_KEY = "weappAuth";
    private final RequestMatcher requiresAuthenticationRequestMatcher =
        new AntPathRequestMatcher("/weapp/preauth", "POST");
    private final PreAuthResponseWriter preAuthResponseWriter = new PreAuthResponseWriter();
    private final ObjectMapper om = new ObjectMapper();
    private final WeappClientService weappClientService;
    private final WeappSessionKeyCache weappSessionKeyCache;
    private final RestOperations restOperations;

    /**
     * Instantiates a new Mini app pre authentication filter.
     *
     * @param weappClientService   the weapp client service
     * @param weappSessionKeyCache the weapp session key cache
     */
    public WeappPreAuthenticationFilter(WeappClientService weappClientService,
        WeappSessionKeyCache weappSessionKeyCache) {
        this.weappClientService = weappClientService;
        this.weappSessionKeyCache = weappSessionKeyCache;
        this.restOperations = new RestTemplate();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        if (response.isCommitted()) {
            return;
        }
        if (requiresAuthenticationRequestMatcher.matches(request)) {
            String clientId = request.getParameter(WEAPP_CLIENT_KEY);
            String jsCode = request.getParameter(JS_CODE_KEY);
            WeappClient weappClient = weappClientService.get(clientId);
            WechatLoginResponse responseEntity = this.getResponse(weappClient, jsCode);

            String openId = responseEntity.getOpenid();
            String sessionKey = responseEntity.getSessionKey();
            weappSessionKeyCache.put(clientId + "::" + openId, sessionKey);
            responseEntity.setSessionKey(null);
            request.setAttribute(ATTRIBUTE_KEY, responseEntity);
            preAuthResponseWriter.write(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static class PreAuthResponseWriter extends ResponseWriter {
        @Override
        protected Result<?> body(HttpServletRequest request) {
            WechatLoginResponse weappAuth = (WechatLoginResponse)request.getAttribute(ATTRIBUTE_KEY);
            return Result.success(weappAuth);
        }
    }

    /**
     * 请求微信服务器登录接口 code2session
     *
     * @param miniAppClient miniAppClient
     * @param jsCode        jsCode
     * @return ObjectNode
     */
    private WechatLoginResponse getResponse(WeappClient miniAppClient, String jsCode) throws JsonProcessingException {
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("appid", miniAppClient.getAppId());
        queryParams.add("secret", miniAppClient.getSecret());
        queryParams.add("js_code", jsCode);
        queryParams.add("grant_type", "authorization_code");

        URI uri = UriComponentsBuilder.fromHttpUrl(ENDPOINT).queryParams(queryParams).build().toUri();
        String response = restOperations.getForObject(uri, String.class);

        if (Objects.isNull(response)) {
            throw new BadCredentialsException("weapp response is null");
        }
        return om.readValue(response, WechatLoginResponse.class);
    }
}
