import {createLocalJWKSet, jwtVerify} from 'jose'
import {assertCurrent, authTask, beginAuth, candidateSession, clearAuth, consumeTransaction,
    saveTransaction, TRANSACTION_TTL} from './adminAuthSession'

let callbackResponse
let starting = false
const keySets = new Map()

/** 在 Router 启动前捕获一次性材料，立刻清除物理 URL 中的 code。 */
export function captureAuthorizationResponse() {
    if (location.pathname !== '/auth/callback') return
    callbackResponse = new URLSearchParams(location.search)
    history.replaceState(null, '', '/#/auth/callback')
}
export function takeAuthorizationResponse() {
    const response = callbackResponse
    callbackResponse = undefined
    return response
}
export function safeReturnPath(value = '/') {
    if (typeof value !== 'string') return '/'
    let decoded
    try { decoded = decodeURIComponent(value) } catch { return '/' }
    if (!decoded.startsWith('/') || decoded.startsWith('//') || decoded.includes('\\') ||
        Array.from(decoded).some(character => character.charCodeAt(0) < 32)) return '/'
    const url = new URL(value, location.origin)
    if (url.origin !== location.origin || /^\/(login|auth\/callback)(\/|[?#]|$)/.test(decoded)) return '/'
    return url.pathname + url.search + url.hash
}
function trustedUrl(value) {
    const url = new URL(value)
    const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
    if (url.username || url.password || url.hash || (url.protocol !== 'https:' && !(url.protocol === 'http:' && loopback))) {
        throw new Error('AUTH_INVALID_CONFIG')
    }
    return url
}
export function authorizationConfig(config) {
    const issuer = trustedUrl(config.OAUTH_AUTHORITY)
    const admin = trustedUrl(config.ADMIN_PUBLIC_URL)
    if (issuer.search || admin.origin !== location.origin || admin.pathname !== '/' || admin.search ||
        !config.OAUTH_CLIENT_ID?.trim() || config.OAUTH_SCOPE?.trim().split(/\s+/).sort().join(' ') !== 'openid profile') {
        throw new Error('AUTH_INVALID_CONFIG')
    }
    return {issuer: config.OAUTH_AUTHORITY, clientId: config.OAUTH_CLIENT_ID, redirectUri: admin.origin + '/auth/callback', scope: 'openid profile system:access'}
}
function endpoint(value, issuer) {
    const url = trustedUrl(value)
    if (url.origin !== new URL(issuer).origin) throw new Error('AUTH_INVALID_METADATA')
    return url.href
}
export function base64url(bytes) {
    return btoa(String.fromCharCode(...new Uint8Array(bytes))).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '')
}
export async function pkceChallenge(verifier) {
    if (!crypto?.subtle || !crypto?.getRandomValues) throw new Error('AUTH_CRYPTO_UNAVAILABLE')
    return base64url(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)))
}
function randomValue() {
    if (!crypto?.subtle || !crypto?.getRandomValues) throw new Error('AUTH_CRYPTO_UNAVAILABLE')
    return base64url(crypto.getRandomValues(new Uint8Array(32)))
}
/** 所有协议请求使用隔离 fetch：无 Cookie、Bearer、租户头或自动重定向。 */
async function protocolJson(url, options = {}, signal) {
    const timeout = new AbortController()
    const abort = () => timeout.abort()
    signal?.addEventListener('abort', abort, {once: true})
    if (signal?.aborted) abort()
    const timer = setTimeout(abort, 10000)
    try {
        const response = await fetch(url, {...options, credentials: 'omit', redirect: 'error', cache: 'no-store', signal: timeout.signal})
        if (!response.ok) throw new Error('AUTH_PROTOCOL_FAILED')
        return await response.json()
    } finally {
        clearTimeout(timer)
        signal?.removeEventListener('abort', abort)
    }
}
async function discovery(config, signal) {
    const metadata = await protocolJson(config.issuer.replace(/\/$/, '') + '/.well-known/openid-configuration', {}, signal)
    if (metadata.issuer !== config.issuer) throw new Error('AUTH_INVALID_METADATA')
    return {authorization: endpoint(metadata.authorization_endpoint, config.issuer),
        token: endpoint(metadata.token_endpoint, config.issuer), jwks: endpoint(metadata.jwks_uri, config.issuer)}
}
export async function startAuthorization(settings, returnPath = '/') {
    if (starting) return
    starting = true
    let generation
    try {
        const config = authorizationConfig(settings)
        generation = beginAuth()
        const signal = authTask(generation)
        const state = randomValue(), nonce = randomValue(), verifier = randomValue()
        const challenge = await pkceChallenge(verifier)
        const metadata = await discovery(config, signal)
        assertCurrent(generation, signal)
        saveTransaction({...config, generation, state, nonce, verifier, createdAt: Date.now(), returnPath: safeReturnPath(returnPath)})
        const url = new URL(metadata.authorization)
        url.search = new URLSearchParams({response_type: 'code', client_id: config.clientId, redirect_uri: config.redirectUri,
            scope: config.scope, state, nonce, code_challenge: challenge, code_challenge_method: 'S256'}).toString()
        location.assign(url.href)
    } catch {
        if (generation !== undefined) clearAuth(generation)
        throw new Error('AUTH_START_FAILED')
    } finally { starting = false }
}
async function signingKeys(uri, signal, refresh = false) {
    const cached = keySets.get(uri)
    if (!refresh && cached && Date.now() - cached.time < 60000) return cached.keys
    const jwks = await protocolJson(uri, {}, signal)
    const keys = createLocalJWKSet(jwks)
    keySets.set(uri, {keys, time: Date.now()})
    return keys
}
export async function validateIdToken(token, accessToken, transaction, jwksUri, signal) {
    const options = {issuer: transaction.issuer, audience: transaction.clientId, algorithms: ['RS256'],
        requiredClaims: ['iss', 'aud', 'exp', 'iat', 'sub', 'nonce'], clockTolerance: 60, maxTokenAge: '10m'}
    let result
    try { result = await jwtVerify(token, await signingKeys(jwksUri, signal), options) }
    catch (error) {
        if (error.code !== 'ERR_JWKS_NO_MATCHING_KEY') throw new Error('AUTH_INVALID_ID_TOKEN')
        result = await jwtVerify(token, await signingKeys(jwksUri, signal, true), options)
    }
    const payload = result.payload
    if (!payload.sub?.trim() || payload.nonce !== transaction.nonce ||
        payload.iat < Math.floor(transaction.createdAt / 1000) - 60 || payload.iat > Math.floor(Date.now() / 1000) + 60 ||
        (Array.isArray(payload.aud) && payload.aud.length > 1 && payload.azp !== transaction.clientId) ||
        (payload.azp !== undefined && payload.azp !== transaction.clientId)) throw new Error('AUTH_INVALID_ID_TOKEN')
    if (payload.at_hash !== undefined) {
        const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(accessToken))
        if (payload.at_hash !== base64url(new Uint8Array(hash).slice(0, 16))) throw new Error('AUTH_INVALID_ID_TOKEN')
    }
}
export async function completeAuthorization(settings, response = takeAuthorizationResponse()) {
    // 无论成功、拒绝、畸形响应还是错误 state，事务都只消费一次。
    const transaction = consumeTransaction()
    if (!transaction) throw new Error('AUTH_MISSING_TRANSACTION')
    try {
        const config = authorizationConfig(settings)
        if (!response || ['code', 'state', 'error', 'iss'].some(key => response.getAll(key).length > 1) ||
            response.has('error') || !response.get('code') || response.get('state') !== transaction.state ||
            (response.has('iss') && response.get('iss') !== transaction.issuer) ||
            config.issuer !== transaction.issuer || config.clientId !== transaction.clientId || config.redirectUri !== transaction.redirectUri ||
            Date.now() - transaction.createdAt > TRANSACTION_TTL) throw new Error('AUTH_INVALID_CALLBACK')
        const signal = authTask(transaction.generation)
        const metadata = await discovery(config, signal)
        const token = await protocolJson(metadata.token, {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({grant_type: 'authorization_code', client_id: transaction.clientId, code: response.get('code'),
                redirect_uri: transaction.redirectUri, code_verifier: transaction.verifier})}, signal)
        const session = candidateSession(token, transaction.generation)
        if (typeof token.id_token !== 'string') throw new Error('AUTH_INVALID_ID_TOKEN')
        await validateIdToken(token.id_token, token.access_token, transaction, metadata.jwks, signal)
        assertCurrent(transaction.generation, signal)
        return {session, returnPath: safeReturnPath(transaction.returnPath)}
    } catch {
        clearAuth(transaction.generation)
        throw new Error('AUTH_CALLBACK_FAILED')
    }
}

// main.js 首个导入：createWebHashHistory 读取初始位置之前完成回调 URL 清理。
if (typeof window !== 'undefined') captureAuthorizationResponse()
