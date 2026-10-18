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

/*
 * @Descripttion: 工具集
 * @version: 1.2
 * @LastEditors: sakuya
 * @LastEditTime: 2022年5月24日00:28:56
 */

import sysConfig from "@/config";

const tool = {}

const STORAGE_VERSION_KEY = 'MACULA_CLOUD_ADMIN_STORAGE_VERSION'
const STORAGE_VERSION = '2'
const LEGACY_STORAGE_KEYS = [
	'USER_INFO', 'MENU', 'PERMISSIONS', 'APP_LANG', sysConfig.TENANT_ID,
	'tenantStoreTenantOptions', 'tenantLabel'
]

function clearLegacyToken() {
	const paths = ['/']
	let path = ''
	for (const segment of location.pathname.split('/').filter(Boolean)) {
		path += `/${segment}`
		paths.push(path)
	}
	for (const cookiePath of paths) {
		document.cookie = `TOKEN=; Max-Age=0; Path=${cookiePath}`
	}
}

// Run before locale, router, request interceptors, or stores read cached state.
export function migrateLegacyStorage() {
	try {
		if (localStorage.getItem(STORAGE_VERSION_KEY) === STORAGE_VERSION) return
		for (const key of LEGACY_STORAGE_KEYS) localStorage.removeItem(key)
		// The old token cannot be used after its related user and permission cache is removed.
		clearLegacyToken()
		localStorage.setItem(STORAGE_VERSION_KEY, STORAGE_VERSION)
	} catch (error) {
		// Browsers can block storage; still invalidate the token when possible.
		clearLegacyToken()
	}
}

migrateLegacyStorage()

/* localStorage */
tool.data = {
	set(key, data, datetime = 0) {
        let cacheValue = {
            content: data,
            datetime: parseInt(datetime) === 0 ? 0 : new Date().getTime() + parseInt(datetime) * 1000
        }
        return localStorage.setItem(key, JSON.stringify(cacheValue))
	},
	get(key) {
        try {
            const value = JSON.parse(localStorage.getItem(key))
            if (value) {
                let nowTime = new Date().getTime()
                if (nowTime > value.datetime && value.datetime != 0) {
                    localStorage.removeItem(key)
                    return null;
                }
                return value.content
            }
            return null
        } catch (err) {
            return null
        }
	},
	remove(key) {
		return localStorage.removeItem(key)
	},
	clear() {
		return localStorage.clear()
	}
}

/*sessionStorage*/
tool.session = {
	set(table, settings) {
		var _set = JSON.stringify(settings)
		return sessionStorage.setItem(table, _set);
	},
	get(table) {
		var data = sessionStorage.getItem(table);
		try {
			data = JSON.parse(data)
		} catch (err) {
			return null
		}
		return data;
	},
	remove(table) {
		return sessionStorage.removeItem(table);
	},
	clear() {
		return sessionStorage.clear();
	}
}

/*cookie*/
tool.cookie = {
	set(name, value, config={}) {
		var cfg = {
			expires: null,
			path: null,
			domain: null,
			secure: false,
			httpOnly: false,
			...config
		}
		var cookieStr = `${name}=${escape(value)}`
		if(cfg.expires){
			var exp = new Date()
			exp.setTime(exp.getTime() + parseInt(cfg.expires) * 1000)
			cookieStr += `;expires=${exp.toGMTString()}`
		}
		if(cfg.path){
			cookieStr += `;path=${cfg.path}`
		}
		if(cfg.domain){
			cookieStr += `;domain=${cfg.domain}`
		}
		document.cookie = cookieStr
	},
	get(name){
		var arr = document.cookie.match(new RegExp("(^| )"+name+"=([^;]*)(;|$)"))
		if(arr != null){
			return unescape(arr[2])
		}else{
			return null
		}
	},
	remove(name){
		var exp = new Date()
		exp.setTime(exp.getTime() - 1)
		document.cookie = `${name}=;expires=${exp.toGMTString()}`
	}
}

/* Fullscreen */
tool.screen = function (element) {
	var isFull = !!(document.webkitIsFullScreen || document.mozFullScreen || document.msFullscreenElement || document.fullscreenElement);
	if(isFull){
		if(document.exitFullscreen) {
			document.exitFullscreen();
		}else if (document.msExitFullscreen) {
			document.msExitFullscreen();
		}else if (document.mozCancelFullScreen) {
			document.mozCancelFullScreen();
		}else if (document.webkitExitFullscreen) {
			document.webkitExitFullscreen();
		}
	}else{
		if(element.requestFullscreen) {
			element.requestFullscreen();
		}else if(element.msRequestFullscreen) {
			element.msRequestFullscreen();
		}else if(element.mozRequestFullScreen) {
			element.mozRequestFullScreen();
		}else if(element.webkitRequestFullscreen) {
			element.webkitRequestFullscreen();
		}
	}
}

/* 复制对象 */
tool.objCopy = function (obj) {
	return JSON.parse(JSON.stringify(obj));
}

/* 日期格式化 */
tool.dateFormat = function (date, fmt='yyyy-MM-dd hh:mm:ss') {
	date = new Date(date)
	var o = {
		"M+" : date.getMonth()+1,                 //月份
		"d+" : date.getDate(),                    //日
		"h+" : date.getHours(),                   //小时
		"m+" : date.getMinutes(),                 //分
		"s+" : date.getSeconds(),                 //秒
		"q+" : Math.floor((date.getMonth()+3)/3), //季度
		"S"  : date.getMilliseconds()             //毫秒
	};
	if(/(y+)/.test(fmt)) {
		fmt=fmt.replace(RegExp.$1, (date.getFullYear()+"").substr(4 - RegExp.$1.length));
	}
	for(var k in o) {
		if(new RegExp("("+ k +")").test(fmt)){
			fmt = fmt.replace(RegExp.$1, (RegExp.$1.length==1) ? (o[k]) : (("00"+ o[k]).substr((""+ o[k]).length)));
		}
	}
	return fmt;
}

/* 千分符 */
tool.groupSeparator = function (num) {
	num = num + '';
	if(!num.includes('.')){
		num += '.'
	}
	return num.replace(/(\d)(?=(\d{3})+\.)/g, function ($0, $1) {
		return $1 + ',';
	}).replace(/\.$/, '');
}

const encoder = new TextEncoder()
const decoder = new TextDecoder('utf-8', {fatal: true})
const AES_GCM_PREFIX = 'gcm1:'
const AES_GCM_IV_LENGTH = 12

function bytesToBase64(bytes) {
	let binary = ''
	for (let index = 0; index < bytes.length; index += 0x8000) {
		binary += String.fromCharCode(...bytes.subarray(index, index + 0x8000))
	}
	return btoa(binary)
}

function base64ToBytes(value) {
	return Uint8Array.from(atob(value), character => character.charCodeAt(0))
}

// Browser cryptography is asynchronous. Keys must be supplied by the caller,
// never embedded in a client-side configuration value.
tool.crypto = {
	SHA256: async (data) => {
		const digest = await globalThis.crypto.subtle.digest('SHA-256', encoder.encode(data))
		return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
	},
	BASE64: {
		encrypt(data) {
			return bytesToBase64(encoder.encode(data))
		},
		decrypt(cipher) {
			return decoder.decode(base64ToBytes(cipher))
		}
	},
	AES: {
		generateKey() {
			return globalThis.crypto.subtle.generateKey(
				{name: 'AES-GCM', length: 256}, false, ['encrypt', 'decrypt']
			)
		},
		importKey(rawKey) {
			const bytes = new Uint8Array(rawKey)
			if (![16, 24, 32].includes(bytes.byteLength)) {
				throw new TypeError('AES key must contain 16, 24, or 32 bytes')
			}
			return globalThis.crypto.subtle.importKey('raw', bytes, 'AES-GCM', false, ['encrypt', 'decrypt'])
		},
		async encrypt(data, key) {
			const iv = globalThis.crypto.getRandomValues(new Uint8Array(AES_GCM_IV_LENGTH))
			const encrypted = new Uint8Array(await globalThis.crypto.subtle.encrypt(
				{name: 'AES-GCM', iv}, key, encoder.encode(data)
			))
			const payload = new Uint8Array(iv.length + encrypted.length)
			payload.set(iv)
			payload.set(encrypted, iv.length)
			return AES_GCM_PREFIX + bytesToBase64(payload)
		},
		async decrypt(cipher, key) {
			if (typeof cipher !== 'string' || !cipher.startsWith(AES_GCM_PREFIX)) {
				throw new TypeError('Unsupported AES ciphertext format')
			}
			const payload = base64ToBytes(cipher.slice(AES_GCM_PREFIX.length))
			if (payload.length < AES_GCM_IV_LENGTH + 16) {
				throw new TypeError('Invalid AES ciphertext')
			}
			const decrypted = await globalThis.crypto.subtle.decrypt(
				{name: 'AES-GCM', iv: payload.subarray(0, AES_GCM_IV_LENGTH)},
				key,
				payload.subarray(AES_GCM_IV_LENGTH)
			)
			return decoder.decode(decrypted)
		}
	}
}

tool.treeFilter = function(tree, func) {
    return tree.map(node => ({...node})).filter(node => {
        node.children = node.children && tool.treeFilter(node.children, func)
        return func(node) || (node.children && node.children.length)
    })
}

export default tool
