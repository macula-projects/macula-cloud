import {beforeEach, describe, expect, it, vi} from 'vitest'

const storageVersionKey = 'MACULA_CLOUD_ADMIN_STORAGE_VERSION'

beforeEach(() => {
    window.history.replaceState({}, '', '/')
    localStorage.clear()
    document.cookie = 'TOKEN=; Max-Age=0; Path=/'
    vi.resetModules()
})

describe('admin local storage migration', () => {
    it('invalidates the old session before cached identity and permissions can be read', async () => {
        const oldCache = JSON.stringify({content: 'legacy-encrypted-value', datetime: 0})
        for (const key of ['USER_INFO', 'MENU', 'PERMISSIONS', 'APP_LANG', 'tenantId',
            'tenantStoreTenantOptions', 'tenantLabel']) {
            localStorage.setItem(key, oldCache)
        }
        localStorage.setItem('other-app-setting', 'keep-me')
        document.cookie = 'TOKEN=old-access-token; Path=/'

        const {default: tool} = await import('@/utils/tool')

        expect(document.cookie).not.toContain('TOKEN=')
        expect(tool.data.get('USER_INFO')).toBeNull()
        expect(tool.data.get('PERMISSIONS')).toBeNull()
        expect(localStorage.getItem('tenantId')).toBeNull()
        expect(localStorage.getItem('other-app-setting')).toBe('keep-me')
        expect(localStorage.getItem(storageVersionKey)).toBe('2')
    })

    it('does not clear data or the token again after migration', async () => {
        const {default: tool, migrateLegacyStorage} = await import('@/utils/tool')
        tool.data.set('APP_LANG', 'en')
        document.cookie = 'TOKEN=new-access-token; Path=/'

        migrateLegacyStorage()

        expect(tool.data.get('APP_LANG')).toBe('en')
        expect(document.cookie).toContain('TOKEN=new-access-token')
    })

    it('clears a token scoped to the admin route as well as the root route', async () => {
        window.history.replaceState({}, '', '/admin/login')
        document.cookie = 'TOKEN=old-subpath-token; Path=/admin'
        const {default: tool} = await import('@/utils/tool')

        expect(document.cookie).not.toContain('TOKEN=')
        expect(tool.data.get('USER_INFO')).toBeNull()
    })

    it('keeps synchronous JSON values and expiry behavior', async () => {
        const {default: tool} = await import('@/utils/tool')
        const userInfo = {id: 123, roles: ['ADMIN']}
        expect(tool.data.set('USER_INFO', userInfo)).toBeUndefined()
        expect(tool.data.get('USER_INFO')).toEqual(userInfo)

        localStorage.setItem('expired', JSON.stringify({content: 'old', datetime: Date.now() - 1}))
        expect(tool.data.get('expired')).toBeNull()
        expect(localStorage.getItem('expired')).toBeNull()
        localStorage.setItem('invalid', '{not-json')
        expect(tool.data.get('invalid')).toBeNull()
    })
})
