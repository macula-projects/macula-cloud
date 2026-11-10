// @vitest-environment node
import {webcrypto} from 'node:crypto'
import {generateKeyPair, exportJWK, SignJWT} from 'jose'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {pkceChallenge, authorizationConfig, safeReturnPath, validateIdToken, completeAuthorization,
    captureAuthorizationResponse, takeAuthorizationResponse, base64url} from '@/utils/adminAuthorizationCode'
import {beginAuth, saveTransaction} from '@/utils/adminAuthSession'
const storage = new Map()
const now = Math.floor(Date.now() / 1000)
let keys, jwks
const settings = {OAUTH_AUTHORITY:'https://iam.example', ADMIN_PUBLIC_URL:'https://admin.example', OAUTH_CLIENT_ID:'admin', OAUTH_SCOPE:'openid profile'}
const transaction = () => ({issuer: settings.OAUTH_AUTHORITY, clientId:'admin', redirectUri:'https://admin.example/auth/callback',
    state:'state', nonce:'nonce', verifier:'synthetic-verifier', createdAt:Date.now(), generation:beginAuth(), returnPath:'/dashboard'})
beforeEach(async () => {
    storage.clear()
    vi.stubGlobal('crypto', webcrypto)
    vi.stubGlobal('sessionStorage', {getItem:key => storage.get(key) ?? null, setItem:(key,value) => storage.set(key,value), removeItem:key => storage.delete(key)})
    vi.stubGlobal('location', {origin:'https://admin.example', pathname:'/', search:''})
    keys = await generateKeyPair('RS256')
    jwks = {keys:[{...await exportJWK(keys.publicKey),kid:crypto.randomUUID(),alg:'RS256'}]}
    vi.stubGlobal('fetch', vi.fn(async () => ({ok:true,json:async () => jwks})))
})
async function signed(overrides = {}, key = keys.privateKey, algorithm = 'RS256') {
    return new SignJWT({iss:'https://iam.example', aud:'admin', sub:'1:7', nonce:'nonce', iat:now, exp:now+300, ...overrides})
        .setProtectedHeader({alg:algorithm, kid:jwks.keys[0].kid}).sign(key)
}
describe('authorization code boundary', () => {
    it('matches RFC7636 S256 vector', async () => {
        expect(await pkceChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM')
    })
    it.each(['//evil.example','https://evil.example','/\\evil.example','/login','/auth/callback','/login?returnTo=/x'])('rejects unsafe return %s', value => {
        expect(safeReturnPath(value)).toBe('/')
    })
    it('accepts only explicit trusted configuration and same-origin callbacks', () => {
        expect(authorizationConfig(settings).redirectUri).toBe('https://admin.example/auth/callback')
        expect(() => authorizationConfig({...settings, ADMIN_PUBLIC_URL:'https://evil.example'})).toThrow()
        expect(() => authorizationConfig({...settings, OAUTH_AUTHORITY:'http://iam.example'})).toThrow()
        expect(() => authorizationConfig({...settings, OAUTH_SCOPE:'profile'})).toThrow()
    })
    it('captures and erases code before Router starts; consumes in memory once', () => {
        vi.stubGlobal('location',{origin:'https://admin.example',pathname:'/auth/callback',search:'?code=synthetic&state=state'})
        const replaceState = vi.fn()
        vi.stubGlobal('history',{replaceState})
        captureAuthorizationResponse()
        expect(replaceState).toHaveBeenCalledWith(null,'','/#/auth/callback')
        expect(takeAuthorizationResponse().get('code')).toBe('synthetic')
        expect(takeAuthorizationResponse()).toBeUndefined()
    })
    it('verifies real RSA signatures and nonce without persisting ID Token', async () => {
        await expect(validateIdToken(await signed(), 'access', transaction(), 'https://iam.example/keys-'+jwks.keys[0].kid)).resolves.toBeUndefined()
        expect([...storage.values()].join('')).not.toContain('eyJ')
    })
    it.each([{iss:'https://evil.example'}, {aud:'other'}, {aud:['admin','other']}, {azp:'other'}, {exp:now-120},
        {iat:now+120}, {iat:now-700}, {sub:''}, {nonce:'wrong'}, {at_hash:'wrong'}])('rejects invalid claims %j', async claims => {
        await expect(validateIdToken(await signed(claims), 'access', transaction(), 'https://iam.example/keys-'+jwks.keys[0].kid)).rejects.toThrow()
    })
    it('rejects a wrong signing key', async () => {
        const wrong = await generateKeyPair('RS256')
        await expect(validateIdToken(await signed({},wrong.privateKey), 'access', transaction(), 'https://iam.example/keys-'+jwks.keys[0].kid)).rejects.toThrow()
    })
    it.each(['code=x&state=wrong','code=x&code=y&state=state','error=access_denied&state=state','code=x&state=state&iss=https://evil.example'])('consumes invalid callbacks once: %s', async query => {
        saveTransaction(transaction())
        await expect(completeAuthorization(settings,new URLSearchParams(query))).rejects.toThrow('AUTH_CALLBACK_FAILED')
        await expect(completeAuthorization(settings,new URLSearchParams(query))).rejects.toThrow('AUTH_MISSING_TRANSACTION')
        expect(fetch).not.toHaveBeenCalled()
    })
    it('uses an isolated form exchange with no client secret, bearer or tenant header', async () => {
        const tx=transaction();saveTransaction(tx)
        const idToken=await signed()
        fetch.mockImplementation(async url => ({ok:true,json:async () => url.endsWith('openid-configuration') ?
            {issuer:settings.OAUTH_AUTHORITY,authorization_endpoint:'https://iam.example/authorize',token_endpoint:'https://iam.example/token',jwks_uri:'https://iam.example/isolated-keys'} :
            url.endsWith('/token') ? {access_token:'synthetic-access',token_type:'Bearer',expires_in:3600,id_token:idToken} : jwks}))
        const result=await completeAuthorization(settings,new URLSearchParams('code=synthetic-code&state=state'))
        expect(result.session.accessToken).toBe('synthetic-access')
        const request=fetch.mock.calls.find(([url])=>url.endsWith('/token'))[1]
        expect(request.credentials).toBe('omit')
        expect(request.redirect).toBe('error')
        expect(request.headers).toEqual({'Content-Type':'application/x-www-form-urlencoded'})
        expect([...request.body.keys()]).toEqual(['grant_type','client_id','code','redirect_uri','code_verifier'])
        expect(request.body.get('grant_type')).toBe('authorization_code')
    })
    it('validates at_hash and azp for multiple audiences', async () => {
        const hash=await crypto.subtle.digest('SHA-256',new TextEncoder().encode('access'))
        const claims={aud:['admin','resource'],azp:'admin',at_hash:base64url(new Uint8Array(hash).slice(0,16))}
        await expect(validateIdToken(await signed(claims),'access',transaction(),'https://iam.example/valid-hash-'+jwkId())).resolves.toBeUndefined()
    })
    it('rejects algorithm substitution', async () => {
        const secret=crypto.getRandomValues(new Uint8Array(32))
        await expect(validateIdToken(await signed({},secret,'HS256'),'access',transaction(),'https://iam.example/algorithm-'+jwkId())).rejects.toThrow()
    })
    it('refreshes a cached JWKS only once for an unknown kid', async () => {
        const uri='https://iam.example/rotation-'+jwkId()
        await validateIdToken(await signed(),'access',transaction(),uri)
        const next=await generateKeyPair('RS256')
        keys=next
        jwks={keys:[{...await exportJWK(next.publicKey),kid:crypto.randomUUID(),alg:'RS256'}]}
        await validateIdToken(await signed(),'access',transaction(),uri)
        expect(fetch).toHaveBeenCalledTimes(2)
    })

})

function jwkId() {return jwks.keys[0].kid}
