package test.dev.macula.cloud.system;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nimbusds.jwt.JWTClaimNames;
import dev.macula.boot.constants.SecurityConstants;
import dev.macula.cloud.system.MaculaSystemApplication;
import dev.macula.cloud.system.form.ApplicationForm;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.bo.ApplicationBO;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.query.ApplicationPageQuery;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@ActiveProfiles("default")
@SpringBootTest(classes = MaculaSystemApplication.class)
@AutoConfigureMockMvc
public class TestSysApplicationRestByAuth {
    // 客户端管理中的登录客户端id
    private static final String CLIENT_ID = "e4da4a32-592b-46f0-ae1d-784310e88423";
    // 客户端管理中的登录客户端id对应的客户端密码
    private static final String CLIENT_SECRET = "secret";
    // 登录用户
    private static final String USER_NAME = "admin";
    // 登录用户密码
    private static final String PASSWORD = "admin";
    // 登录路径
    private static final String OAUTH_URL = "http://localhost:9010/oauth2/token";
    // 真实登录请求路径模板
    private static final String GET_TOKEN_URI_TEMPLATE
            = "%s?grant_type=password&username=%s&password=%s&client_id=%s&client_secret=%s&scope=message.read";
    // token认证路径
    private static final String OAUTH_VERIFY_URL = "http://localhost:9010/oauth2/introspect";
    // 真实token认证请求路径模板
    private static final String OAUTH_VERIFY_URI_TEMPLATE = "%s?token=%s";
    private static final RestTemplate REST_TEMPLATE = new RestTemplate();
    // 集成测试请求头Authorization的 token值，配置则直接使用来进行接口访问，不配置则通过上文的USER_NAME+PASSWORD进行登录获取
    private static String tokenValue;
    @SpyBean
    private SysApplicationMapper sysApplicationMapper;
    @Autowired
    private MockMvc mvc;
    @MockBean
    private JwtDecoder jwtDecoder;


    @BeforeAll
    public static void setup(){
        // 不是所有类型的token都可以通过rest请求实时获取， 比如：通过授权码获取的token
        if(StringUtils.isNotBlank(tokenValue)){
            return;
        }
        String getTokenUri = String.format(GET_TOKEN_URI_TEMPLATE, OAUTH_URL, USER_NAME, PASSWORD, CLIENT_ID,
                CLIENT_SECRET);
        TokenVo tokenVo = REST_TEMPLATE.postForObject(getTokenUri, null, TokenVo.class);
        Assertions.assertNotNull(tokenVo, "获取token请求失败");
        Assertions.assertNotNull(tokenVo.getAccess_token(), "获取token_value失败");
        tokenValue = tokenVo.getAccess_token();
    }

    @BeforeEach
    public void handlerOauthOpaqueToken(){
        String getUserInfoUri = String.format(OAUTH_VERIFY_URI_TEMPLATE, OAUTH_VERIFY_URL, tokenValue);
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setBasicAuth(CLIENT_ID, CLIENT_SECRET);
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        TokenUserInfoVo userInfoVo = REST_TEMPLATE.postForObject(getUserInfoUri, requestEntity, TokenUserInfoVo.class);
        Assertions.assertTrue(userInfoVo != null && userInfoVo.isActive(), "token已过期");
        // jwt 生成请参数当前macula上下文的AddJwtGlobalFilter.generateJwtToken,当前样例使用的版本是5.0.11
        // 注意仿照的是jwt对象内信息，无须使用jwtEncoder
        Jwt jwt = getCurVersionGwJwt(userInfoVo);
        Mockito.when(jwtDecoder.decode(tokenValue)).thenReturn(jwt);
    }

    @Test
    public void testListApplications() throws Exception{
        Page<ApplicationBO> res = new Page<>();
        res.setSize(1);
        res.setTotal(1);
        List<ApplicationBO> records = new ArrayList<>();
        ApplicationBO sys = new ApplicationBO();
        sys.setApplicationName("bff-abd");
        sys.setSk("test");
        sys.setCode("bff-abd");
        sys.setManager("admin");
        sys.setUseAttrs(false);
        sys.setMaintainer("admin");
        records.add(sys);
        res.setRecords(records);
        doReturn(res).when(sysApplicationMapper).listApplicationPages(any(Page.class), any(ApplicationPageQuery.class));

        mvc.perform(get("/api/v1/app?pageNo=1&pageSize=1&keywords=bff")
                        .header("Authorization", "Bearer " + tokenValue)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.total", is("1")))
                .andExpect(jsonPath("$.data.records[0].applicationName", containsString("bff")))
                .andDo(print());
    }

    @Test
    public void testSaveApplication() throws Exception{
        doReturn(1).when(sysApplicationMapper).insert(any(SysApplication.class));

        ApplicationForm applicationForm = new ApplicationForm();
        applicationForm.setApplicationName("test");
        applicationForm.setCode("test");
        applicationForm.setSk("test");
        applicationForm.setManager("admin");
        applicationForm.setMobile("13800138000");
        applicationForm.setUseAttrs(false);
        mvc.perform(post("/api/v1/app")
                        .header("Authorization", "Bearer " + tokenValue)
                        .contentType(MediaType.APPLICATION_JSON).content(JSONObject.toJSONString(applicationForm)))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andDo(print());
    }

    @Test
    public void testUpdateApplication() throws Exception{
        doReturn(1).when(sysApplicationMapper).updateById(any(SysApplication.class));

        ApplicationForm applicationForm = new ApplicationForm();
        applicationForm.setApplicationName("test1");
        applicationForm.setCode("test1");
        applicationForm.setSk("test1");
        applicationForm.setManager("admin");
        applicationForm.setUseAttrs(false);
        mvc.perform(put("/api/v1/app/1")
                        .header("Authorization", "Bearer " + tokenValue)
                        .contentType(MediaType.APPLICATION_JSON).content(JSONObject.toJSONString(applicationForm)))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andDo(print());
    }

    @Test
    public void testDeleteApplications() throws Exception{
        doReturn(1).when(sysApplicationMapper).deleteById(any(SysApplication.class));

        mvc.perform(delete("/api/v1/app/1")
                        .header("Authorization", "Bearer " + tokenValue)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andDo(print());
    }

    @Test
    public void testAddMaintainer() throws Exception{
        doReturn(1).when(sysApplicationMapper).updateById(any(SysApplication.class));
        SysApplication sys = new SysApplication();
        sys.setId(1L);
        doReturn(sys).when(sysApplicationMapper).selectById(1L);

        ApplicationForm applicationForm = new ApplicationForm();
        applicationForm.setApplicationName("test1");
        applicationForm.setCode("test1");
        applicationForm.setSk("test1");
        applicationForm.setManager("admin");
        applicationForm.setUseAttrs(false);
        applicationForm.setMaintainer("admin");
        mvc.perform(put("/api/v1/app/addMaintainer/1")
                        .header("Authorization", "Bearer " + tokenValue)
                        .contentType(MediaType.APPLICATION_JSON).content(JSONObject.toJSONString(applicationForm)))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andDo(print());
    }

    @Test
    public void testValidtorAppCode() throws Exception{
        doReturn(0L).when(sysApplicationMapper).selectCount(any(Wrapper.class));

        mvc.perform(get("/api/v1/app/validtor/appCode?appId=1&appCode=test")
                        .header("Authorization", "Bearer " + tokenValue)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", is(true)))
                .andDo(print());
    }

    private Jwt getCurVersionGwJwt(TokenUserInfoVo userInfoVo){
        Map<String, Object> claimsMap = JSONObject.parseObject(JSONObject.toJSONString(userInfoVo));
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        OAuth2IntrospectionAuthenticatedPrincipal principal = new OAuth2IntrospectionAuthenticatedPrincipal(claimsMap,
                authorities);

        // 取自源码macula-boot-starter-cloud-gateway中AddJwtGlobalFilter.generateJwtToken
        JwtClaimsSet.Builder jwtClaimBuilder = JwtClaimsSet.builder();
        // copy oauth2服务器返回的attribute
        principal.getAttributes().forEach(jwtClaimBuilder::claim);

        Instant issuedAt = Instant.now();
        // 处理时间
        jwtClaimBuilder.expiresAt(issuedAt.plus(30, ChronoUnit.DAYS));
        jwtClaimBuilder.issuedAt(issuedAt);

        // 如果缺少jti、deptId、dataScope、nickname，设置默认值
        if (!principal.getAttributes().containsKey(JWTClaimNames.JWT_ID)) {
            jwtClaimBuilder.id(UUID.randomUUID().toString());
        }
        if (!principal.getAttributes().containsKey(SecurityConstants.JWT_NICKNAME_KEY)) {
            jwtClaimBuilder.claim(SecurityConstants.JWT_NICKNAME_KEY, principal.getName());
        }
        if (!principal.getAttributes().containsKey(SecurityConstants.JWT_DEPTID_KEY)) {
            jwtClaimBuilder.claim(SecurityConstants.JWT_DEPTID_KEY, SecurityConstants.ROOT_NODE_ID);
        }
        if (!principal.getAttributes().containsKey(SecurityConstants.JWT_DATASCOPE_KEY)) {
            jwtClaimBuilder.claim(SecurityConstants.JWT_DATASCOPE_KEY, 0);
        }
        // 如果principal没有issue，需要设置jwt的issue
        //if (!principal.getAttributes().containsKey(JWTClaimNames.ISSUER)) {
        //    jwtClaimBuilder.claim(JWTClaimNames.ISSUER, issuerUri);
        //}
        // 外部定制claims
        //jwtClaimsCustomizer.customize(jwtClaimBuilder);

        JwsAlgorithm jwsAlgorithm = SignatureAlgorithm.RS256;
        JwsHeader.Builder jwsHeaderBuilder = JwsHeader.with(jwsAlgorithm);

        JwtClaimsSet claims = jwtClaimBuilder.build();
        JwsHeader jwsHeader = jwsHeaderBuilder.build();
        return new Jwt(tokenValue, claims.getIssuedAt(), claims.getExpiresAt(), jwsHeader.getHeaders(),
                claims.getClaims());
    }


    @Data
    @ToString
    static class TokenVo {
        private String access_token;
        private String token_type;
        private String refresh_token;
        private Integer expires_in;
        private List<String> scope;
    }

    @Data
    @ToString
    static class TokenUserInfoVo{
        private String sub;
        private String scope;
        private boolean active;
        private String userType;
        private long exp;
        private String jti;
        private List<String> authorities;
    }

}
