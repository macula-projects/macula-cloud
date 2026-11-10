import {webcrypto} from 'node:crypto'
import {createPinia,setActivePinia} from 'pinia'
import {beforeEach, describe, expect, it, vi} from 'vitest'
vi.mock('@/api/model/common/auth',()=>({default:{getUserInfo:{get:vi.fn()},getTenantOptions:{get:vi.fn()},getRoutes:{get:vi.fn()}}}))
import auth from '@/api/model/common/auth'
import {initializeSession, ensureSessionInitialized, filterAuthorizedMenu} from '@/utils/adminAuthBootstrap'
import {beginAuth,candidateSession,clearAuth,readSession,commitSession} from '@/utils/adminAuthSession'
import tool from '@/utils/tool'
const ok=data=>({success:true,data})
const candidate=()=>candidateSession({access_token:'synthetic',token_type:'Bearer',expires_in:3600},beginAuth())
beforeEach(()=>{
    vi.stubGlobal('crypto',webcrypto); sessionStorage.clear(); setActivePinia(createPinia()); vi.clearAllMocks()
    auth.getUserInfo.get.mockResolvedValue(ok({username:'root',roles:['ROOT'],perms:['*:*:*']}))
    auth.getTenantOptions.get.mockResolvedValue(ok([{value:1,label:'Tenant'}]))
    auth.getRoutes.get.mockResolvedValue(ok([{path:'/dashboard',meta:{roles:['ROOT']}}]))
})
describe('permission initialization',()=>{
    it('commits only after user, tenants and authorized routes succeed',async()=>{
        const session=candidate()
        expect(readSession()).toBeNull()
        await initializeSession(session)
        expect(readSession()).toEqual(session)
        expect(tool.data.get('tenantId')).toBe(1)
        expect(localStorage.getItem('USER_INFO')).toBeNull()
        expect(auth.getRoutes.get.mock.calls[0][0].headers).toEqual({Authorization:'Bearer synthetic',tenantId:'1'})
    })
    it.each(['tenants','permissions','api'])('fails closed for %s',async failure=>{
        if(failure==='tenants')auth.getTenantOptions.get.mockResolvedValue(ok([]))
        if(failure==='permissions')auth.getRoutes.get.mockResolvedValue(ok([]))
        if(failure==='api')auth.getUserInfo.get.mockRejectedValue(new Error('offline'))
        await expect(initializeSession(candidate())).rejects.toThrow()
        expect(readSession()).toBeNull()
        expect(tool.data.get('USER_INFO')).toBeNull()
    })
    it('late successful or failed initialization cannot revive a logged out session',async()=>{
        let resolve
        auth.getUserInfo.get.mockReturnValueOnce(new Promise(r=>{resolve=r}))
        const session=candidate(), promise=initializeSession(session)
        clearAuth()
        resolve(ok({username:'old',roles:['ROOT'],perms:['*:*:*']}))
        await expect(promise).rejects.toThrow('AUTH_SUPERSEDED')
        expect(readSession()).toBeNull()
    })
    it('late old failure cannot clear a newer successful session',async()=>{
        let reject
        auth.getUserInfo.get.mockReturnValueOnce(new Promise((_,r)=>{reject=r}))
        const old=candidate(),promise=initializeSession(old)
        const current=candidate()
        await initializeSession(current)
        reject(new Error('old offline'))
        await expect(promise).rejects.toThrow('old offline')
        expect(readSession()).toEqual(current)
    })
    it('preserves a committed session on 503 and retries initialization after recovery',async()=>{
        const session=candidate();commitSession(session)
        const unavailable=Object.assign(new Error('dependency unavailable'),{status:503})
        auth.getUserInfo.get.mockRejectedValueOnce(unavailable)
        await expect(ensureSessionInitialized()).rejects.toBe(unavailable)
        expect(readSession()).toEqual(session)
        await ensureSessionInitialized()
        expect(auth.getUserInfo.get).toHaveBeenCalledTimes(2)
        expect(readSession()).toEqual(session)
    })
    it.each([401,503])('does not publish a candidate session when initialization returns %s',async status=>{
        auth.getUserInfo.get.mockRejectedValueOnce(Object.assign(new Error('unavailable'),{response:{status}}))
        await expect(initializeSession(candidate())).rejects.toThrow()
        expect(readSession()).toBeNull()
    })
    it('coalesces reload restoration into one permission initialization',async()=>{
        const session=candidate();commitSession(session)
        await Promise.all([ensureSessionInitialized(),ensureSessionInitialized()])
        expect(auth.getUserInfo.get).toHaveBeenCalledTimes(1)
    })
    it('retains positive and negated role semantics without ROOT bypass',()=>{
        const menu=[{path:'/allowed',meta:{roles:['ADMIN']}},{path:'/blocked',meta:{roles:['BLOCKED']}},{path:'/public'}]
        expect(filterAuthorizedMenu(menu,['ADMIN','!BLOCKED']).map(x=>x.path)).toEqual(['/allowed','/public'])
        expect(filterAuthorizedMenu(menu,['ROOT']).map(x=>x.path)).toEqual(['/public'])
    })
})
