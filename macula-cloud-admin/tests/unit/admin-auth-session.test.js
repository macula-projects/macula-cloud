import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest'
import {webcrypto} from 'node:crypto'
import * as session from '@/utils/adminAuthSession'
beforeEach(() => {vi.stubGlobal('crypto', webcrypto); sessionStorage.clear(); localStorage.clear(); vi.useFakeTimers()})
afterEach(() => {vi.useRealTimers(); vi.unstubAllGlobals()})
const token = {access_token: 'synthetic-access', token_type: 'Bearer', expires_in: 3600}
describe('tab authentication boundary', () => {
    it('stores only an expiring access session in sessionStorage', () => {
        const value = session.candidateSession(token, session.beginAuth())
        session.commitSession(value)
        expect(session.readSession()).toEqual(value)
        expect(localStorage.length).toBe(0)
        expect(document.cookie).not.toContain('TOKEN=')
        vi.advanceTimersByTime(3600001)
        expect(session.readSession()).toBeNull()
    })
    it.each([0, -1, 86401, 1.1, '3600', undefined])('rejects invalid expires_in %s', expires_in => {
        expect(() => session.candidateSession({...token, expires_in})).toThrow('AUTH_INVALID_TOKEN')
    })
    it('consumes transactions only once and expires after ten minutes', () => {
        const transaction = {generation: session.beginAuth(), createdAt: Date.now(), verifier: 'synthetic'}
        session.saveTransaction(transaction)
        expect(session.consumeTransaction()).toEqual(transaction)
        expect(session.consumeTransaction()).toBeNull()
        session.saveTransaction(transaction)
        vi.advanceTimersByTime(600001)
        expect(session.consumeTransaction()).toBeNull()
    })
    it('aborts old initialization and ignores stale commits, failure clears and 401 contexts', () => {
        const oldGeneration = session.beginAuth()
        const signal = session.authTask(oldGeneration)
        const old = session.candidateSession(token, oldGeneration)
        const current = session.candidateSession(token, session.beginAuth())
        session.commitSession(current)
        expect(signal.aborted).toBe(true)
        expect(() => session.commitSession(old)).toThrow('AUTH_SUPERSEDED')
        expect(session.clearAuth(oldGeneration)).toBe(false)
        expect(session.ownsSession(old)).toBe(false)
        expect(session.ownsSession(current)).toBe(true)
    })
    it('fails closed when session storage cannot persist authentication', () => {
        const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {throw new Error('blocked')})
        expect(() => session.beginAuth()).toThrow('AUTH_STORAGE_UNAVAILABLE')
        spy.mockRestore()
    })
    it('attaches Bearer only to the trusted API and ignores stale 401 responses', async () => {
        const {isTrustedApiUrl} = await import('@/utils/request')
        const {default:axios} = await import('axios')
        expect(isTrustedApiUrl('/api/system/api/v1/users/me')).toBe(true)
        for (const url of ['/api-other/x','https://external.example/api/x','http://127.0.0.1:9010/oauth2/token']) {
            expect(isTrustedApiUrl(url)).toBe(false)
        }
        const request = axios.interceptors.request.handlers.filter(Boolean).at(-1).fulfilled
        const response = axios.interceptors.response.handlers.filter(Boolean).at(-1).rejected
        const old = session.candidateSession(token, session.beginAuth())
        session.commitSession(old)
        const sent = request({url:'/api/system/api/v1/users/me',headers:{},method:'post'})
        expect(sent.headers.Authorization).toBe('Bearer synthetic-access')
        const external = request({url:'https://external.example/api/x',headers:{Authorization:'Bearer synthetic-access',tenantId:'1'},method:'post'})
        expect(external.headers).not.toHaveProperty('Authorization')
        expect(external.headers).not.toHaveProperty('tenantId')
        const current = session.candidateSession(token, session.beginAuth())
        session.commitSession(current)
        await expect(response({config:sent,response:{status:401}})).rejects.toEqual({status:401})
        expect(session.readSession()).toEqual(current)
        const fresh = request({url:'/api/system/api/v1/users/me',headers:{},method:'post'})
        await expect(response({config:fresh,response:{status:401}})).rejects.toEqual({status:401})
        expect(session.readSession()).toBeNull()
    })

})
