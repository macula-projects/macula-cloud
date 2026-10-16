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
		identity: '身份提供商',
		identitySocial: '社交身份提供商',
		identityEnterprise: '企业身份提供商',
		identityPending: '配置仅保存，尚未接入 IAM 第三方登录。',
		security: '通用安全',
		securityPending:
			'这些配置目前仅保存，尚未接入 IAM 或网关运行时，暂未生效。',
		password: '密码策略',
		passwordPending:
			'策略目前仅保存，尚未接入 IAM 密码设置和登录流程，暂未生效。',
		message: '消息设置',
		messageDescription:
			'管理邮件、短信服务及模板。测试发送只向本次输入的地址或手机号投递，服务端返回提交状态，最终收件请在收件端确认。',
		mailTemplate: '邮件模板',
		mailService: '邮件服务',
		smsTemplate: '短信模板',
		smsService: '短信服务',
		oauth: 'IAM 接入应用',
		oauthWarning: '客户端配置变更不会撤销已经签发的授权和令牌。'
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
		language_msg: '翻译进行中，暂翻译了本视图的文本'
	}
}
