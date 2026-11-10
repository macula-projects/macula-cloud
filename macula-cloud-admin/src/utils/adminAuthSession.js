/** 标签页内的认证边界；不依赖路由、Pinia 或 API。 */
export const AUTH_KEYS = ['USER_INFO', 'MENU', 'PERMISSIONS', 'tenantId', 'tenantLabel', 'tenantStoreTenantOptions']
export const SESSION_KEY = 'MACULA_ADMIN_SESSION_V1'
export const TRANSACTION_KEY = 'MACULA_ADMIN_AUTH_TRANSACTION_V1'
const GENERATION_KEY = 'MACULA_ADMIN_AUTH_GENERATION'
export const TRANSACTION_TTL = 10 * 60 * 1000
const listeners = new Set()
let controller
let expiryTimer
let memoryGeneration = 0
let storageFailed = false

export function authGeneration() {
    try {
        const stored = Number(sessionStorage.getItem(GENERATION_KEY) || 0)
        if (!Number.isSafeInteger(stored) || stored < 0) throw new Error('AUTH_INVALID_STORAGE')
        memoryGeneration = Math.max(memoryGeneration, stored)
    } catch { storageFailed = true }
    return memoryGeneration
}
export function ownsGeneration(generation) {
    return generation === authGeneration()
}
export function onAuthCleared(listener) {
    listeners.add(listener)
    return () => listeners.delete(listener)
}
export function clearAuth(expectedGeneration) {
    if (expectedGeneration !== undefined && !ownsGeneration(expectedGeneration)) return false
    controller?.abort()
    clearTimeout(expiryTimer)
    memoryGeneration = authGeneration() + 1
    try {
        sessionStorage.setItem(GENERATION_KEY, String(memoryGeneration))
        sessionStorage.removeItem(SESSION_KEY)
        sessionStorage.removeItem(TRANSACTION_KEY)
        for (const key of AUTH_KEYS) sessionStorage.removeItem(key)
        storageFailed = false
    } catch { storageFailed = true }
    for (const listener of listeners) listener()
    return true
}
export function beginAuth() {
    clearAuth()
    if (storageFailed) throw new Error('AUTH_STORAGE_UNAVAILABLE')
    return authGeneration()
}
export function authTask(generation) {
    if (!ownsGeneration(generation)) throw new Error('AUTH_SUPERSEDED')
    controller?.abort()
    controller = new AbortController()
    return controller.signal
}
export function assertCurrent(generation, signal) {
    if (signal?.aborted || !ownsGeneration(generation)) throw new Error('AUTH_SUPERSEDED')
}
export function candidateSession(token, generation = authGeneration()) {
    if (!token || token.token_type?.toLowerCase() !== 'bearer' || typeof token.access_token !== 'string' ||
        !token.access_token.trim() || !Number.isInteger(token.expires_in) || token.expires_in <= 0 || token.expires_in > 86400) {
        throw new Error('AUTH_INVALID_TOKEN')
    }
    return {version: 1, sessionId: crypto.randomUUID(), generation, accessToken: token.access_token,
        expiresAt: Date.now() + token.expires_in * 1000}
}
export function readSession() {
    if (storageFailed) return null
    let session
    try { session = JSON.parse(sessionStorage.getItem(SESSION_KEY)) } catch { return null }
    if (!session) return null
    if (session.version !== 1 || !session.sessionId || typeof session.accessToken !== 'string' ||
        !Number.isFinite(session.expiresAt) || !ownsGeneration(session.generation) || session.expiresAt <= Date.now()) {
        clearAuth(session.generation)
        return null
    }
    return session
}
export function ownsSession(context) {
    const current = readSession()
    return Boolean(current && context && current.sessionId === context.sessionId && current.generation === context.generation)
}
export function commitSession(session) {
    assertCurrent(session.generation)
    if (storageFailed) throw new Error('AUTH_STORAGE_UNAVAILABLE')
    if (session.expiresAt <= Date.now()) throw new Error('AUTH_EXPIRED')
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
    clearTimeout(expiryTimer)
    expiryTimer = setTimeout(() => {
        if (ownsSession(session)) clearAuth(session.generation)
    }, Math.max(0, session.expiresAt - Date.now()))
}
export function saveTransaction(transaction) {
    sessionStorage.setItem(TRANSACTION_KEY, JSON.stringify(transaction))
}
export function consumeTransaction() {
    const raw = sessionStorage.getItem(TRANSACTION_KEY)
    sessionStorage.removeItem(TRANSACTION_KEY)
    try {
        const transaction = JSON.parse(raw)
        if (!transaction || !ownsGeneration(transaction.generation) || !Number.isFinite(transaction.createdAt) ||
            Date.now() - transaction.createdAt > TRANSACTION_TTL || transaction.createdAt > Date.now()) return null
        return transaction
    } catch { return null }
}
