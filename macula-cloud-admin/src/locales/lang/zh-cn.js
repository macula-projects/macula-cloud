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
		sessionValidTime: '会话有效时间（秒）',
		verifyCodeValidTime: '验证码有效时间（秒）',
		rememberMeValidTime: '记住我有效时间（秒）',
		sessionMaximum: '用户并发数',
		loginFailureDuration: '连续登录失败持续时间（分钟）',
		loginFailureCount: '连续登录失败次数',
		autoUnlockTime: '自动解锁时间（分钟）',
		contentSecurityPolicy: '内容安全策略',
		passwordBiggestLength: '密码最大长度',
		passwordLeastLength: '密码最小长度',
		passwordComplexity: '密码复杂度',
		customWeakPassword: '自定义弱密码',
		weakPasswordCheck: '弱密码检查',
		includeAccountCheck: '账户信息检查',
		notSameChars: '连续相同字符上限',
		historyPasswordCheck: '历史密码检查',
		historyPasswordCheckCount: '历史密码检查次数',
		illegalSequenceCheck: '非法序列检查',
		passwordValidDays: '密码有效天数',
		passwordValidWarnBeforeDays: '过期前提醒天数',
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
