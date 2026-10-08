import {describe, expect, it, vi} from 'vitest'

vi.mock('@/utils/request', () => ({
	default: {
		get: vi.fn((url, data) => Promise.resolve({url, data})),
		post: vi.fn((url, data) => Promise.resolve({url, data})),
		put: vi.fn((url, data) => Promise.resolve({url, data})),
		delete: vi.fn((url, data) => Promise.resolve({url, data}))
	}
}))

import api from '@/api/model/tinyid/management'
import managementPage from '@/views/tinyid/management/index.vue?raw'
import applicationPanel from '@/views/tinyid/management/ApplicationPanel.vue?raw'
import businessPanel from '@/views/tinyid/management/BusinessPanel.vue?raw'
import businessCreateDialog from '@/views/tinyid/management/BusinessCreateDialog.vue?raw'
import auditLogPanel from '@/views/tinyid/management/AuditLogPanel.vue?raw'
import applicationCreateDialog from '@/views/tinyid/management/ApplicationCreateDialog.vue?raw'
import applicationAuthorizationDialog from '@/views/tinyid/management/ApplicationAuthorizationDialog.vue?raw'

describe('TinyID management', () => {
	it('uses the gateway TinyID prefix for management APIs', async () => {
		const result = await api.applications.list({page: 1})
		expect(result.url).toContain('/tinyid/api/v1/admin/apps')
	})

	it('exposes guarded delete actions without disable or token rotation', async () => {
		expect(applicationPanel).toContain('删除应用')
		expect(applicationPanel).toContain('其他实例将在定时刷新或重启后收敛')
		expect(applicationPanel).not.toContain('停用应用')
		expect(applicationPanel).not.toContain('轮换 Token')
		expect(applicationPanel).toContain('增加授权')
		expect(businessPanel).toContain('删除业务')
		expect(businessPanel).toContain('Number(instance.maxId) === 0')

		const applicationDelete = await api.applications.delete(8)
		const businessDelete = await api.businesses.delete('order/type')
		expect(applicationDelete.url).toContain('/tinyid/api/v1/admin/apps/8')
		expect(businessDelete.url).toContain('/tinyid/api/v1/admin/businesses/order%2Ftype')
	})

	it('guides administrators to configure businesses before applications', () => {
		expect(managementPage).toContain("ref('businesses')")
		expect(managementPage).toContain('先配置发号业务，再创建接入应用并授权业务')
		expect(applicationCreateDialog).toContain('请先在“发号业务”完成至少一个 biz_type 配置')
	})

	it('uses aggregate businesses and server-assigned read-only remainders', () => {
		expect(businessPanel).not.toContain('source-picker')
		expect(businessPanel).toContain('scope.row.dataSources')
		expect(businessPanel).toContain('remainder（只读）')
		expect(businessCreateDialog).toContain("delta: 10")
		expect(businessCreateDialog).not.toContain('dataSourceKey')
		expect(businessCreateDialog).not.toContain('v-model="form.remainder"')
	})

	it('does not send generic idempotency keys from management forms', () => {
		expect(applicationCreateDialog).not.toContain('idempotencyKey')
		expect(applicationAuthorizationDialog).not.toContain('idempotencyKey')
		expect(businessCreateDialog).not.toContain('idempotencyKey')
		expect(applicationCreateDialog).not.toContain('crypto.randomUUID')
	})

	it('uses responses already unwrapped by the shared HTTP client', () => {
		const components = [
			applicationPanel,
			businessPanel,
			auditLogPanel,
			applicationCreateDialog,
			applicationAuthorizationDialog
		]
		components.forEach(component => {
			expect(component).not.toContain('response.data')
			expect(component).not.toContain('sources.data')
		})
	})
})
