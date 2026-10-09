import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {ElMessage} from 'element-plus'
import http from '@/utils/request'
import BusinessCreateDialog from '@/views/tinyid/management/BusinessCreateDialog.vue'

vi.mock('element-plus', async () => ({
	...await vi.importActual('element-plus'),
	ElMessage: {error: vi.fn()}
}))

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

	it('checks unified results before reading business data', () => {
		const components = [
			applicationPanel,
			businessPanel,
			auditLogPanel,
			applicationCreateDialog,
			applicationAuthorizationDialog
		]
		components.forEach(component => {
			expect(component).toContain('if (!response.success)')
			expect(component).toContain('response.data.records')
		})
	})

	function createBusinessDialog() {
		return mount(BusinessCreateDialog, {global: {stubs: {
			ElDialog: {props: ['modelValue'], template: '<div v-if="modelValue"><slot/><slot name="footer"/></div>'},
			ElForm: {template: '<form><slot/></form>', methods: {validate: () => Promise.resolve(true)}},
			ElFormItem: {template: '<div><slot/></div>'},
			ElButton: {props: ['loading'], template: '<button type="button" :disabled="loading"><slot/></button>'},
			ElInput: true, ElInputNumber: true, ElAlert: true
		}}})
	}

	it('keeps the form open on business failure and closes only after success', async () => {
		http.post.mockResolvedValueOnce({success: false, cause: '容量不足', msg: '失败'})
		const wrapper = createBusinessDialog()
		try {
			wrapper.vm.$.exposed.open()
			await flushPromises()
			await wrapper.findAll('button').at(-1).trigger('click')
			await flushPromises()
			expect(ElMessage.error).toHaveBeenCalledWith('容量不足')
			expect(wrapper.emitted('success')).toBeUndefined()
			expect(wrapper.find('form').exists()).toBe(true)
			expect(wrapper.findAll('button').at(-1).element.disabled).toBe(false)
			http.post.mockResolvedValueOnce({success: true, data: {bizType: 'order'}})
			await wrapper.findAll('button').at(-1).trigger('click')
			await flushPromises()
			expect(wrapper.emitted('success')).toHaveLength(1)
			expect(wrapper.find('form').exists()).toBe(false)
		} finally { wrapper.unmount() }
	})

	it('releases saving state after a transport failure without reporting success', async () => {
		http.post.mockRejectedValueOnce(new Error('network unavailable'))
		const wrapper = createBusinessDialog()
		try {
			wrapper.vm.$.exposed.open()
			await flushPromises()
			await wrapper.findAll('button').at(-1).trigger('click')
			await flushPromises()
			expect(wrapper.find('form').exists()).toBe(true)
			expect(wrapper.findAll('button').at(-1).element.disabled).toBe(false)
			expect(wrapper.emitted('success')).toBeUndefined()
		} finally { wrapper.unmount() }
	})
})
