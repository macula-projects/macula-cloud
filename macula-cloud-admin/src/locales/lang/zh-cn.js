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
	adminSettings: {
		securityPolicy: '安全策略',
		securityPolicyPending:
			'这些配置目前仅保存，尚未接入 IAM 或网关运行时，暂未生效。'
	},
	securityPolicyPage: {
		basic: '通用安全',
		defense: '防御策略',
		password: '密码策略',
		pageDescription:
			'统一管理当前租户的登录会话、防御和密码规则，各页签独立保存。',
		basicDescription:
			'设置当前租户的用户会话、验证码和“记住我”登录状态有效期。',
		defenseDescription: '设置登录失败锁定、自动解锁和浏览器内容安全策略。',
		passwordDescription:
			'设置密码长度、复杂度、有效期及弱密码等规则，保证用户修改密码时使用符合要求的密码。',
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
		language_msg: '翻译进行中，暂翻译了本视图的文本',
	}
}
