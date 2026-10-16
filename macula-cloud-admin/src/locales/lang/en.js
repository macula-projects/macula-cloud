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
		securityPolicy: 'Security Policy',
		securityPolicyPending:
			'These settings are stored but not yet enforced by IAM or Gateway.'
	},
	securityPolicyPage: {
		basic: 'Basic Security',
		defense: 'Defense Policy',
		password: 'Password Policy',
		sessionValidTime: 'Session validity (seconds)',
		verifyCodeValidTime: 'Verification code validity (seconds)',
		rememberMeValidTime: 'Remember-me validity (seconds)',
		sessionMaximum: 'Concurrent sessions per user',
		loginFailureDuration: 'Login failure window (minutes)',
		loginFailureCount: 'Consecutive login failures',
		autoUnlockTime: 'Automatic unlock time (minutes)',
		contentSecurityPolicy: 'Content Security Policy',
		passwordBiggestLength: 'Maximum password length',
		passwordLeastLength: 'Minimum password length',
		passwordComplexity: 'Password complexity',
		customWeakPassword: 'Custom weak passwords',
		weakPasswordCheck: 'Weak password check',
		includeAccountCheck: 'Account information check',
		notSameChars: 'Maximum repeated characters',
		historyPasswordCheck: 'Password history check',
		historyPasswordCheckCount: 'Password history count',
		illegalSequenceCheck: 'Illegal sequence check',
		passwordValidDays: 'Password validity (days)',
		passwordValidWarnBeforeDays: 'Expiry warning (days)',
		saveBasic: 'Save Basic Security',
		saveDefense: 'Save Defense Policy',
		savePassword: 'Save Password Policy',
		retry: 'Reload',
		cspPlaceholder: "For example: default-src 'self'",
		weakPasswordPlaceholder: 'One weak password per line',
		requiredNumber: 'Enter a valid number',
		operationFailed: 'Operation failed',
		loadFailed: 'Failed to load the security policy',
		saveSuccess: 'Saved; runtime enforcement is not connected yet',
		invalidLength: 'Minimum password length cannot exceed the maximum',
		invalidWarnDays: 'Expiry warning days must be less than password validity days',
		complexity0: 'Any password',
		complexity1: 'Must contain numbers and letters',
		complexity2: 'Must contain numbers and uppercase letters',
		complexity3: 'Must contain numbers, upper/lowercase letters and special characters',
		complexity4: 'At least two of numbers, letters and special characters',
		complexity5: 'At least three of numbers, upper/lowercase letters and special characters'
	},
	login: {
		slogan: 'High performance / delicate / grace',
		describe: 'Vue3 + element plus based front-end solutions in the background.',
		signInTitle: 'Sign in',
		accountLogin: 'Account sign in',
		mobileLogin: 'Mobile sign in',
		rememberMe: 'Remember me',
		forgetPassword: 'Forget password',
		signIn: 'Sign in',
		signInOther: 'Sign in with',
		userPlaceholder: 'user / phone / email',
		userError: 'Please input a user name',
		PWPlaceholder: 'Please input a password',
		PWError: 'Please input a password',
		admin: 'Administrator',
		user: 'User',
		mobilePlaceholder: 'Mobile',
		mobileError: 'Please input mobile',
		smsPlaceholder: 'SMS Code',
		smsError: 'Please input sms code',
		smsGet: 'Get SMS Code',
		smsSent: 'SMS sent to mobile number',
		noAccount: 'No account?',
		createAccount: 'Create a new account',
		wechatLoginTitle: 'QR code sign in',
		wechatLoginMsg: 'Please use wechat to scan and log in | Auto scan after 3 seconds of simulation',
		wechatLoginResult: 'Scanned | Please click authorize login in the device'
	},
	user: {
		dynamic: 'Dynamic',
		info: 'User Info',
		settings: 'Settings',
		nightmode: 'night mode',
		nightmode_msg: 'Suitable for low light environment,The current night mode is beta',
		language: 'language',
		language_msg: 'Translation in progress,Temporarily translated the text of this view',
	}
}
