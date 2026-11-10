# 本轮平台合同证据

核对日期：2026-11-09。记录静态官方证据，不代表真实企业/设备联调。

| 平台/能力 | HTTP合同、字段与凭据 | 证据与边界 |
| --- | --- | --- |
| 企微成员 | GET https://qyapi.weixin.qq.com/cgi-bin/user/get，应用access_token、userid；userid为企业内成员标识 | 官方 https://developer.work.weixin.qq.com/document/path/90196 正文已通过只读HTTP获取。新自建/代开发应用自2022-06-20调整后不返回手机号、邮箱等敏感字段，需要本人授权。未发现可直接当业务工号的job_number合同。 |
| 企微网页身份 | GET https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo，应用access_token、code；返回userid | 官方 https://developer.work.weixin.qq.com/document/path/91023 正文已读取。user_ticket仅在snsapi_privateinfo及适用可见范围时返回，供敏感资料接口使用。当前snsapi_base不会取得该票据。用户明确本轮暂缓敏感资料，仅成员ID绑定。 |
| 企微双网页入口 | 浏览器CorpApp：login.work.weixin.qq.com/wwlogin/sso/login；端内网页：open.weixin.qq.com/connect/oauth2/authorize | 沿用当前已实现固定端点和state绑定，不与JSAPI/SDK Code混用，不改scope。 |
| 钉钉企业资料 | POST https://oapi.dingtalk.com/topapi/v2/user/get，应用access_token、JSON userid；result包含userid/job_number/mobile/email | 已安装官方com.aliyun:alibaba-dingtalk-service-sdk:2.0.0 sources.jar，OapiV2UserGetRequest及Response的@ApiField精确确认这些协议字段与方法名。当前官方 https://open.dingtalk.com/document/development/query-user-details 返回外壳；SDK只能证明字段/请求合同，企业权限和真实返回仍须应用联调。缺失字段不自动匹配。 |
| 飞书企业资料 | GET https://open.feishu.cn/open-apis/contact/v3/users/{open_id}?user_id_type=open_id，Bearer tenant_access_token | 官方 https://open.larkoffice.com/document/server-docs/contact-v3/user/get?appId=cli_a80d82191ff1500d 正文可读，2026-04-03更新：user_id/open_id分别表达，邮箱/手机号/工号等受字段权限和应用通讯录范围约束，41050为无用户权限，不能当非成员。 |
| 微信小程序 | 登录code→GET sns/jscode2session；手机号授权code→POST wxa/business/getuserphonenumber（应用Token） | 保留官方 https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html 及 https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-info/phone-number/getPhoneNumber.html。两个Code由现有code_type独立核验；当前网页工具无法读取正文，本轮不变更已有HTTP合同，不声称核实新增版本。 |

企微原始官方HTML及去标签正文仅保存在/private/tmp/provider-90196.html、provider-91023.html及对应txt；不提交厂商页面副本。钉钉证据只读取官方SDK源码，不安装、执行或新增依赖。应用权限、真实Code、成员状态和设备交互均未实测。
