import {createHash} from 'node:crypto'
import {readFileSync} from 'node:fs'
import {createContext, runInContext} from 'node:vm'
import {describe, expect, it} from 'vitest'

import QRCode from '@/components/scQrCode/qrcode'
import AdminLayout from '@/layout/index.vue'
import config, {mergeRuntimeConfig} from '@/config'

describe('lint cleanup behavior', () => {
    it.each([
        ['HELLO', 25, 'df5973a576c5bc77c40ed73624dfa9c62bac9072a82f3feb90e4092dfc15e49e'],
        ['Macula Cloud 安全迁移', 33, 'c122f34b01f487282fa2a4ed0e72bd512cc818abbb0cf80816797dae8686f403'],
        ['https://macula.dev/admin?tenant=42', 37, '23dd9c799744bcbdc0e5272f701ea3a2b63ee0e43b7c4a729493491bb429ea3f']
    ])('preserves the QRCode matrix for %s', (value, size, digest) => {
        const qr = new QRCode(document.createElement('div'), {text: value, useSVG: true})
        const matrix = qr._oQRCode.modules.map(row => row.map(cell => cell ? '1' : '0').join('')).join('\n')
        expect(qr._oQRCode.getModuleCount()).toBe(size)
        expect(createHash('sha256').update(matrix).digest('hex')).toBe(digest)
    })

    it('retains the external script configuration contract and merge precedence', () => {
        const script = readFileSync('public/config.js', 'utf8')
        const context = createContext({})
        runInContext(script, context)
        const runtime = runInContext('APP_CONFIG', context)
        expect(runtime).toEqual({})
        expect(runInContext('globalThis.APP_CONFIG === APP_CONFIG', context)).toBe(true)
        expect(mergeRuntimeConfig({...config}, {APP_NAME: 'Runtime Admin'}).APP_NAME).toBe('Runtime Admin')
    })

    it('keeps legacy route names mapped to renamed cache components', () => {
        const mapped = AdminLayout.computed.cacheComponentNames.call({
            keepLiveRoute: ['dashboard', 'application', 'dept', 'dic', 'log', 'role', 'tenant', 'user', 'custom']
        })
        expect(mapped).toEqual([
            'HomeDashboardPage', 'SystemApplicationPage', 'SystemDeptPage', 'SystemDictPage',
            'SystemLogPage', 'SystemRolePage', 'SystemTenantPage', 'SystemUserPage', 'custom'
        ])
    })
})
