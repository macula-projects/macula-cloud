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

export default {
  identitySourceIntegration: {"codeTypeHelp": "小程序前端按实际获取方式提交 code_type：wx.login 使用登录 Code，getPhoneNumber 使用手机号 Code；两者共享应用凭据，但不能混用。", "wecomMemberOnly": "企业微信当前支持成员 ID 匹配；手机号、邮箱需要额外本人授权，后续接入。", "sourceField": "身份源字段", "targetField": "IAM 匹配字段", "selectSourceField": "选择已核验的外部属性", "selectTargetField": "选择IAM账号字段", "mappingHelp": "可配置多条映射；任一命中且所有命中指向同一账号时自动绑定。冲突或多账号匹配时需验证已有账号，不覆盖用户资料。", "legacyAdmission": "该来源原有的企业成员准入要求继续执行。", "sourceUnavailable": "该接入方式的客户端合同尚待核实，暂不可启用。", "wechatWebsiteHelp": "用于普通浏览器扫码登录，使用微信开放平台「网站应用」的 AppID / AppSecret；微信端内网页请另建「微信公众号」来源。", "wechatOfficialHelp": "用于微信端内网页授权，使用公众号的 AppID / AppSecret；与网站应用分别配置，不共用凭据。", "wecomWebHelp": "同一自建应用覆盖浏览器扫码与企业微信端内网页授权，共用 CorpID / AgentId / Secret；需分别配置企业微信授权登录与网页授权可信域名。", "flow": "平台接口版本 / Flow", "flowRequired": "请选择已核实的接口版本", "directory": "通讯录策略", "organization": "固定组织标识", "organizationHelp": "飞书填写应用所属企业真实的 tenant_key，不是文字“tenant_key”；用于校验登录者所属企业，保存后不能直接替换。", "unverified": "该 Flow 尚未核实，只能保留为禁用记录；不提供可直接使用的登录示例。", "codeType": "第三方认证能力", "codeTypeRequired": "选择已关联且可用的第三方认证能力", "nonceBoundary": "扩展请求的 nonce 只用于请求与 ID Token 的关联，不证明第三方 Code 与 nonce 绑定。", "identityOnly": "仅验证身份（保持现有准入）", "optional": "补充资料可选（仍拒绝非成员/停用）", "required": "实时企业成员准入（必须查询成功）", "background": "已绑定成员后台刷新资料", "addMapping": "添加绑定规则", "removeMapping": "移除规则", "appLinkClientCallback": "供业务App代码使用：AppLink回调须同时登记在飞书后台和此Client白名单。无需开启IAM授权码登录；两种用途共用白名单，但平台PKCE与IAM PKCE分别处理。"},
 applicationProtocols: {authenticationTitle:"认证与授权",authenticationHelp:"客户端认证证明应用身份，授权方式决定应用如何取得令牌。",callbacksTitle:"回调与浏览器来源",permissionsTitle:"权限与登录保护",oidcScopeHelp:"openid 用于身份认证，profile 用于请求基础资料；资源 API 的业务权限按登记目录选择。",oauthScopeHelp:"profile 授权读取用户名和昵称，使用平台 /api/v1/profile；系统 API 另需 system:access 及角色权限。",oauthScopeEmpty:"当前授权方式没有适用的 Scope，请调整授权方式或登记实际资源能力。",default:'默认协议',enabled:'启用此协议',savedWhenDisabled:'关闭后保留配置，再次启用重新校验',noEnabled:'尚无已启用协议，应用只能保存为草稿',endpoints:'协议端点信息'},
	adminSettings: {
		identity: '身份提供商',
		identitySocial: '社交身份提供商',
		identityEnterprise: '企业身份提供商',
		identityPending: '身份源凭据统一维护，应用在登录控制中关联已启用渠道。',
		message: '消息设置',
		messageDescription:
			'管理邮件、短信服务及模板。测试发送只向本次输入的地址或手机号投递，服务端返回提交状态，最终收件请在收件端确认。',
		mailTemplate: '邮件模板',
		mailService: '邮件服务',
		smsTemplate: '短信模板',
		smsService: '短信服务',
		oauth: 'IAM 接入应用',
		oauthWarning: '客户端配置变更不会撤销已经签发的授权和令牌。',
		securityPolicy: '安全策略',
		securityPolicyPending:
			'这些配置目前仅保存，尚未接入 IAM 或网关运行时，暂未生效。'
	},
	messageSettingsPage: {
		exchangeMail: "Exchange（企业自建）",
		netease163Mail: "163 免费邮箱",
		qqMail: "QQ 邮箱",
		customHttpSms: "自定义短信服务",
		postUrl: "POST URL",
		authHeaders: "认证 Header",
		headerName: "Header 名称",
		headerValue: "认证值",
		addHeader: "添加 Header",
		removeHeader: "移除",
		headersRequired: "请配置认证 Header",
		keepHeaders: "已配置；全部留空保持原值，填写后整体替换",
		invalidHeaders: "Header 名称、值或重复配置无效",
		authorizationCodeHint: "请先开启 SMTP 服务，使用客户端授权码作为密码。",
		customSmsHint: "配置可信服务地址，建议使用 HTTPS；模板配置沿用现有方式。",

		retry: '重新加载',
		emptyTemplates: '暂无模板',
		scene: '场景',
		subject: '主题',
		custom: '自定义',
		yes: '是',
		no: '否',
		actions: '操作',
		configure: '配置',
		preview: '浏览',
		enableService: '启用服务',
		provider: '服务商',
		smtpHost: 'SMTP 服务器',
		port: '端口',
		securityMode: '安全模式',
		mailAccount: '用户名 / 邮箱',
		senderAddress: '发件人邮箱',
		smsSignature: '短信签名',
		region: '地域',
		smsAppId: '短信应用 ID',
		password: '密码',
		keepSecret: '留空保持原值',
		save: '保存',
		templateCode: '模板 ID / CODE',
		test: '测试',
		configureMail: '配置邮件模板',
		customMail: '自定义邮件',
		variables: '可用变量',
		none: '无',
		senderName: '发件人显示名',
		htmlContent: 'HTML 内容',
		cancel: '取消',
		confirm: '确定',
		mailPreview: '邮件模板预览',
		testSend: '测试发送',
		phone: '手机号',
		submit: '提交',
		customSmtp: '自定义 SMTP',
		aliyunMail: '阿里云企业邮',
		tencentMail: '腾讯云企业邮',
		neteaseMail: '网易企业邮',
		aliyunSms: '阿里云',
		tencentSms: '腾讯云',
		loadFailed: '加载失败',
		saveSuccess: '保存成功',
		submitted: '已提交',
		invalidForm: '请完整填写必填配置',
		credentialRequired: '请输入服务凭据',
		templateCodeRequired: '请输入模板 ID / CODE',
		invalidPhone: '请输入有效手机号',
		operationFailed: '操作失败',
		scenes: {
			MAIL_BIND: '绑定邮箱',
			MAIL_CHANGE_BIND: '修改绑定邮箱',
			MAIL_FORGOT_PASSWORD: '忘记密码',
			MAIL_CHANGE_PASSWORD: '修改密码',
			MAIL_RESET_SUCCESS: '管理员重置密码成功',
			MAIL_LOGIN: '登录验证',
			MAIL_PASSWORD_EXPIRY: '密码到期提醒',
			MAIL_WELCOME: '欢迎邮件',
			SMS_BIND: '绑定手机号',
			SMS_BIND_SUCCESS: '绑定手机号成功',
			SMS_CHANGE_PHONE: '修改手机号',
			SMS_FORGOT_PASSWORD: '忘记密码',
			SMS_CHANGE_PASSWORD: '修改密码',
			SMS_RESET_SUCCESS: '管理员重置密码成功',
			SMS_LOGIN: '登录验证',
			SMS_WELCOME: '欢迎短信',
			SMS_PASSWORD_EXPIRY: '密码到期提醒'
		},
		sceneDescriptions: {
			MAIL_BIND: '每当用户绑定邮箱时，都会发送此电子邮件。',
			MAIL_CHANGE_BIND: '每当用户需要修改绑定邮箱、发送验证码时，都会发送此电子邮件。',
			MAIL_FORGOT_PASSWORD: '每当用户忘记密码需要重置时，都会发送此电子邮件。',
			MAIL_CHANGE_PASSWORD: '每当用户要求更改密码时，都会发送此电子邮件。',
			MAIL_RESET_SUCCESS: '每当管理员重置用户密码成功时，都会发送此电子邮件。',
			MAIL_LOGIN: '每当用户选择邮箱验证码登录时，都会发送此电子邮件。',
			MAIL_PASSWORD_EXPIRY: '用户距离上次修改密码有效期即将过期时，都会发送此电子邮件。',
			MAIL_WELCOME: '一旦用户验证其电子邮件地址，将发送此电子邮件。如果验证电子邮件已关闭，则会在用户第一次注册或登录时发送。'
		}
	},
	securityPolicyPage: {
		basic: '通用安全',
		defense: '防御策略',
		password: '密码策略',
		seconds: '秒',
		minutes: '分钟',
		days: '天',
		times: '次',
		sessions: '个会话',
		characters: '个字符',
		sessionValidTime: '会话有效时间',
		sessionValidTimeHelp: '用户登录会话的有效时长，超时后需要重新登录。',
		verifyCodeValidTime: '验证码有效时间',
		verifyCodeValidTimeHelp: '验证码在登录、找回密码等验证场景中的有效时长。',
		rememberMeValidTime: '记住我有效时间',
		rememberMeValidTimeHelp:
			'用户选择“记住我”后登录状态的有效时长；设置为 0 表示不启用长期记忆。',
		sessionMaximum: '用户并发数',
		sessionMaximumHelp:
			'同一用户允许同时在线的会话数量；达到上限后，旧会话将按认证服务规则失效。',
		loginFailureDuration: '连续登录失败持续时间',
		loginFailureDurationHelp:
			'统计连续登录失败次数的时间窗口，超过该时长后重新计算。',
		loginFailureCount: '连续登录失败次数',
		loginFailureCountHelp:
			'在统计时间窗口内达到此次数时，当前账户将被自动锁定。',
		autoUnlockTime: '自动解锁时间',
		autoUnlockTimeHelp: '账户因连续登录失败被锁定后，经过此时长自动解除锁定。',
		contentSecurityPolicy: '内容安全策略',
		contentSecurityPolicyHelp:
			'浏览器内容安全策略（CSP）可限制脚本、样式、图片等资源来源，用于降低跨站脚本和数据注入风险。',
		passwordBiggestLength: '密码最大长度',
		passwordBiggestLengthHelp:
			'新密码允许的最大字符数，必须大于或等于密码最小长度。',
		passwordLeastLength: '密码最小长度',
		passwordLeastLengthHelp:
			'新密码必须达到的最小字符数，取值范围为 6 到 128。',
		passwordComplexity: '密码复杂度',
		passwordComplexityHelp: '选择新密码必须包含的字符类型组合。',
		customWeakPassword: '自定义弱密码',
		customWeakPasswordHelp: '补充系统内置弱密码库，每行填写一个弱密码。',
		weakPasswordCheck: '弱密码检查',
		weakPasswordCheckHelp:
			'开启后，新密码不能命中系统内置弱密码库或下方自定义弱密码。',
		includeAccountCheck: '账户信息检查',
		includeAccountCheckHelp:
			'开启后，新密码不能包含用户名、手机号、邮箱前缀或姓名拼音等账户信息。',
		notSameChars: '连续相同字符上限',
		notSameCharsHelp:
			'限制密码中连续出现相同字符的最大数量；设置为 0 表示不检查。',
		historyPasswordCheck: '历史密码检查',
		historyPasswordCheckHelp: '开启后，新密码不能与指定范围内的历史密码相同。',
		historyPasswordCheckCount: '历史密码检查次数',
		historyPasswordCheckCountHelp:
			'指定需要比对的最近历史密码数量，仅在历史密码检查开启时生效。',
		illegalSequenceCheck: '非法序列检查',
		illegalSequenceCheckHelp:
			'开启后，禁止使用键盘、字母或数字等容易猜测的连续序列。',
		passwordValidDays: '密码有效天数',
		passwordValidDaysHelp:
			'密码自设置之日起的有效天数；设置为 0 表示永不过期。',
		passwordValidWarnBeforeDays: '过期前提醒天数',
		passwordValidWarnBeforeDaysHelp:
			'在密码过期前多少天提醒用户，必须小于密码有效天数。',
		saveBasic: '保存通用安全',
		saveDefense: '保存防御策略',
		savePassword: '保存密码策略',
		retry: '重新加载',
		cspPlaceholder: "例如：default-src 'self'",
		weakPasswordPlaceholder: '每行一个弱密码',
		requiredNumber: '请输入有效数值',
		operationFailed: '操作失败',
		loadFailed: '安全策略加载失败',
		saveSuccess: '保存成功，当前暂未接入运行时',
		invalidLength: '密码最小长度不得大于最大长度',
		invalidWarnDays: '过期前提醒天数必须小于密码有效天数',
		complexity0: '任意密码',
		complexity1: '必须包含数字和字母',
		complexity2: '必须包含数字和大写字母',
		complexity3: '必须包含数字、大小写字母和特殊字符',
		complexity4: '数字、字母和特殊字符至少两类',
		complexity5: '数字、大小写字母和特殊字符至少三类'
	},
	login: {
        iamLogin: '前往 IAM 登录',
        iamDescription: '通过统一身份平台登录管理控制台。',
        iamFailed: '登录未完成，请重新登录或联系管理员。',
        iamRetry: '返回登录',
        iamInitializing: '正在确认身份并加载访问权限…',

		slogan: '高性能 / 精致 / 优雅',
		describe: '基于Vue3 + Element-Plus 的中后台前端解决方案。',
		signInTitle: '用户登录',
		accountLogin: '账号登录',
		mobileLogin: '手机号登录',
		rememberMe: '24小时免登录',
		forgetPassword: '忘记密码',
		signIn: '登录',
		signInOther: '其他登录方式',
		userPlaceholder: '用户名 / 手机 / 邮箱',
		userError: '请输入用户名',
		PWPlaceholder: '请输入密码',
		PWError: '请输入密码',
		admin: '管理员',
		user: '用户',
		mobilePlaceholder: '手机号码',
		mobileError: '请输入手机号码',
		smsPlaceholder: '短信验证码',
		smsError: '请输入短信验证码',
		smsGet: '获取验证码',
		smsSent: '已发送短信至手机号码',
		noAccount: '还没有账号?',
		createAccount: '创建新账号',
		wechatLoginTitle: '二维码登录',
		wechatLoginMsg: '请使用微信扫一扫登录 | 模拟3秒后自动扫描',
		wechatLoginResult: '已扫描 | 请在设备中点击授权登录'
	},
	user: {
		dynamic: '近期动态',
		info: '个人信息',
		settings: '设置',
		nightmode: '黑夜模式',
		nightmode_msg: '适合光线较弱的环境，当前黑暗模式为beta版本',
		language: '语言',
		language_msg: '翻译进行中，暂翻译了本视图的文本'
	},
 applicationBoundary: {
    webLoginMode: '网页登录模式', browserMode: '浏览器', inAppMode: '端内',inApp: '端内应用只能选择一个端内登录能力，不提供IAM标准登录页。', native: '移动客户端只能选择一个SDK或AppLink来源，可同时启用IAM内置登录。', miniapp: '小程序只能关联一个本平台来源；微信登录Code与手机号Code可同源并存。'}

}
