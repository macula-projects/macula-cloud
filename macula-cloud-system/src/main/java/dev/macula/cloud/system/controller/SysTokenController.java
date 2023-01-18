package dev.macula.cloud.system.controller;

import com.alibaba.fastjson.JSONObject;
import dev.macula.boot.result.ApiResultCode;
import dev.macula.boot.result.Result;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.hibernate.validator.constraints.Length;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.List;

/**
 * 登录token的控制器
 * @author qiuyuhao
 * @date 2023.01.16
 */
@Tag(name = "登录接口", description = "登录接口")
@RequestMapping("/api/token")
@RestController
@Slf4j
public class SysTokenController {
  private static final HttpHeaders DEFAULT_HEADERS;
  static {
    DEFAULT_HEADERS = new HttpHeaders();
    DEFAULT_HEADERS.set("Authorization", getBaseAuthHeader("client", "secret"));
  }

  @Operation(summary = "获取用户登录token")
  @PostMapping
  public Result postToken(@RequestBody @Valid SysTokenController.UserDto userVo){
    MultiValueMap<String, String> params = new LinkedMultiValueMap<String, String>();
    params.add("grant_type", "password");
    params.add("loginType", "password");
    params.add("terminalType", "PC");
    params.add("username", userVo.getUsername());
    params.add("password", userVo.getPassword());
    HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<MultiValueMap<String, String>>(params, DEFAULT_HEADERS);
    ResponseEntity<JSONObject> responseEntity= getRestTemplate().exchange("https://goauth-dev.infinitus.com.cn/oauth/token", HttpMethod.POST, requestEntity, JSONObject.class);
    int code = responseEntity.getStatusCodeValue();
    if(code == 200) {
      return Result.success(responseEntity.getBody());
    }
    return Result.failed(ApiResultCode.FAILED, responseEntity);
  }

  @Operation(summary = "验证登录token是否有效")
  @GetMapping("/userInfo")
  public Result userInfo(){
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("userName", SecurityUtils.getCurrentUser());
    jsonObject.put("dashboard", 0);
    jsonObject.put("role", SecurityUtils.getRoles());
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
    @Length(min = 1,message = "用户名不能为空")
    private String username;
  }

  /**
   * 与oauthApi共用同一个缓存的restTemplate
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
