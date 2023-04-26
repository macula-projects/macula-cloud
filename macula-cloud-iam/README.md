# Macula Cloud IAM 认证中心

提供基于OAUTH/CAS/OIDC/SAML协议的统一认证服务，所有服务经过网站认证

## 认证方式

负责认证资源Owner方提供的认证凭据，为下层交互协议生成token或者ticket做准备

### 用户名密码认证

```
POST /login
username 用户名
password 密码
要带上openid/unionid可以绑定
```

### 短信认证(TODO)

```
POST /login/captcha
phone   手机号码
captcha 短信验证码
要带上openid/unionid可以绑定
```

### 微信授权手机号登录(TODO)

```
POST /login/weapp/phone
新版
code（后端通过code获取手机号），再根据手机号查询对应用户
旧版（加密数据，后端用session_key解密得到手机号）
encryptedData
iv
要带上openid/unionid可以绑定
```

### 已绑定小程序登录认证(TODO)

```
POST /login/weapp/code
code（后端调用code2session获取openid/unionid,如果没有绑定则调用不成功)

```

### 已绑定企微小程序登录认证(TODO)

```
POST /login/qyweapp/code
code（后端调用code2session获取userid，没有绑定登录不成功）
```

###  

## 协议

协议层不负责用户身份认证，只按照协议生成对应的token或者ticket返回给客户端

### OAuth2协议

提供基于OAuth2.1协议的实现，基于[spring-authorization-server 0.41](https://github.com/spring-projects/spring-authorization-server/tree/0.4.x)
实现，默认支持的grant_type为：

- authorization_code
- client_credentials
- refresh_token
- 兼容旧OAuth2.0扩展的password等类型

> 注意：基于安全原因，OAuth2.1默认已经取消了对于grant_type为password及implicit的支持。

### CAS协议（TODO）

### SAML协议（TODO）

## 二次开发

### 认证方式扩展

主要参考authentication和grant两个包中类

### 协议扩展

参考config中的有关各协议的配置

### 登录界面定制

在 src/resources/templates/目录中，可以考虑在client配置中设置不同client需要的认证方式，从而出现不同登录界面

### 用户身份源定制

实现UserAuthInfoService接口，可以根据UserType访问不同的身份源

```java
public interface UserAuthInfoService {
    /**
     * 获取用户信息，上层覆盖该方法
     *
     * @param userType 用户类型
     * @param username 用户名
     * @return 返回指定用户类型和用户名的用户信息
     */
    UserAuthInfo getUserAuthInfo(UserTypeEnum userType, String username);
}

public enum UserTypeEnum {
    MEMBER("member", "会员"), 
    EMPLOYEE("employee", "行政员工"), 
    ODC("odc", "外包人员"), 
    SUPPLIER("supplier", "供应商");
    ...
}
```

> 注意：<br/>
> 为了兼容旧版oauth2服务器，grant包提供password的grant_type方式，不过该方式以后会逐步取消