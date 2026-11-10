import {describe,expect,it} from 'vitest'
import config,{mergeRuntimeConfig} from '@/config'
describe('public SPA configuration',()=>{
    it('has OIDC scope and no browser secret or demo credentials',()=>{
        expect(config.OAUTH_SCOPE).toBe('openid profile')
        for(const key of ['OAUTH_CLIENT_SECRET','DEMO_USERNAME','DEMO_PASSWORD']) expect(config).not.toHaveProperty(key)
        expect(config.OAUTH_CLIENT_ID).not.toBe('e4da4a32-592b-46f0-ae1d-784310e88423')
    })
    it('applies runtime public configuration last',()=>{
        expect(mergeRuntimeConfig({OAUTH_CLIENT_ID:'build'},{OAUTH_CLIENT_ID:'runtime'}).OAUTH_CLIENT_ID).toBe('runtime')
    })
})
