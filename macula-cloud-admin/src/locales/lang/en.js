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
		pageDescription:
			'Manage session, defense, and password rules for the current tenant. Each tab is saved independently.',
		basicDescription:
			'Configure user sessions, verification codes, and remember-me validity for the current tenant.',
		defenseDescription:
			'Configure login-failure lockout, automatic unlock, and browser content security policy.',
		passwordDescription:
			'Configure password length, complexity, expiry, history, and weak-password rules.',
		seconds: 'seconds',
		minutes: 'minutes',
		days: 'days',
		times: 'times',
		sessions: 'sessions',
		characters: 'characters',
		sessionValidTime: 'Session validity',
		sessionValidTimeHelp:
			'How long a signed-in user session remains valid before sign-in is required again.',
		verifyCodeValidTime: 'Verification code validity',
		verifyCodeValidTimeHelp:
			'How long a verification code remains valid for sign-in and recovery flows.',
		rememberMeValidTime: 'Remember-me validity',
		rememberMeValidTimeHelp:
			'How long remember-me sign-in remains valid; set to 0 to disable extended retention.',
		sessionMaximum: 'Concurrent sessions per user',
		sessionMaximumHelp:
			'Maximum concurrent sessions for one user; older sessions expire according to IAM rules.',
		loginFailureDuration: 'Login failure window',
		loginFailureDurationHelp:
			'Time window used to count consecutive login failures before the count resets.',
		loginFailureCount: 'Consecutive login failures',
		loginFailureCountHelp:
			'The account is locked when failures reach this number within the configured window.',
		autoUnlockTime: 'Automatic unlock time',
		autoUnlockTimeHelp:
			'How long a login-failure lock remains in place before the account is unlocked automatically.',
		contentSecurityPolicy: 'Content Security Policy',
		contentSecurityPolicyHelp:
			'A browser CSP restricts script, style, image, and other resource origins to reduce XSS and injection risks.',
		passwordBiggestLength: 'Maximum password length',
		passwordBiggestLengthHelp:
			'Maximum permitted password length; it must be at least the minimum length.',
		passwordLeastLength: 'Minimum password length',
		passwordLeastLengthHelp:
			'Minimum required password length, from 6 through 128 characters.',
		passwordComplexity: 'Password complexity',
		passwordComplexityHelp:
			'Select the combination of character classes required in a new password.',
		customWeakPassword: 'Custom weak passwords',
		customWeakPasswordHelp:
			'Extend the built-in weak-password dictionary with one password per line.',
		weakPasswordCheck: 'Weak password check',
		weakPasswordCheckHelp:
			'Reject passwords found in the built-in or custom weak-password dictionary.',
		includeAccountCheck: 'Account information check',
		includeAccountCheckHelp:
			'Reject passwords containing the username, phone, email prefix, or name spelling.',
		notSameChars: 'Maximum repeated characters',
		notSameCharsHelp:
			'Maximum number of identical consecutive characters; set to 0 to disable this check.',
		historyPasswordCheck: 'Password history check',
		historyPasswordCheckHelp:
			'Prevent a new password from matching a password in the configured history range.',
		historyPasswordCheckCount: 'Password history count',
		historyPasswordCheckCountHelp:
			'Number of recent passwords to compare when password history checking is enabled.',
		illegalSequenceCheck: 'Illegal sequence check',
		illegalSequenceCheckHelp:
			'Reject easy-to-guess keyboard, alphabetic, or numeric sequences.',
		passwordValidDays: 'Password validity',
		passwordValidDaysHelp:
			'Days from the password-set date until expiry; set to 0 for no expiry.',
		passwordValidWarnBeforeDays: 'Expiry warning',
		passwordValidWarnBeforeDaysHelp:
			'Days before expiry to warn the user; must be less than password validity.',
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
		invalidWarnDays:
			'Expiry warning days must be less than password validity days',
		complexity0: 'Any password',
		complexity1: 'Must contain numbers and letters',
		complexity2: 'Must contain numbers and uppercase letters',
		complexity3:
			'Must contain numbers, upper/lowercase letters and special characters',
		complexity4: 'At least two of numbers, letters and special characters',
		complexity5:
			'At least three of numbers, upper/lowercase letters and special characters'
	},
	login: {
		slogan: 'High performance / delicate / grace',
		describe:
			'Vue3 + element plus based front-end solutions in the background.',
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
		wechatLoginMsg:
			'Please use wechat to scan and log in | Auto scan after 3 seconds of simulation',
		wechatLoginResult: 'Scanned | Please click authorize login in the device'
	},
	user: {
		dynamic: 'Dynamic',
		info: 'User Info',
		settings: 'Settings',
		nightmode: 'night mode',
		nightmode_msg:
			'Suitable for low light environment,The current night mode is beta',
		language: 'language',
		language_msg:
			'Translation in progress,Temporarily translated the text of this view',
	}
}
