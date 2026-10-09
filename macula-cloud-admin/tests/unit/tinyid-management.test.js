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
import businessPanel from '@/views/tinyid/management/BusinessPanel.vue?raw'
import businessCreateDialog from '@/views/tinyid/management/BusinessCreateDialog.vue?raw'
import auditLogPanel from '@/views/tinyid/management/AuditLogPanel.vue?raw'

describe('TinyID management', () => {
	it('uses the gateway TinyID prefix for management APIs', async () => {
		const result = await api.businesses.list({page: 1})
		expect(result.url).toContain('/tinyid/api/v1/admin/businesses')
	})

	it('exposes guarded business deletion without application APIs', async () => {
		expect(businessPanel).toContain('删除业务')
		expect(businessPanel).toContain('Number(instance.maxId) === 0')

		const businessDelete = await api.businesses.delete('order/type')
		expect(businessDelete.url).toContain('/tinyid/api/v1/admin/businesses/order%2Ftype')
	})

	it('keeps only business and audit management', () => {
		expect(managementPage).toContain("ref('businesses')")
		expect(managementPage).not.toContain('ApplicationPanel')
		expect(managementPage).not.toContain('接入应用')
		expect(managementPage).toContain('审计日志')
		expect(api).not.toHaveProperty('applications')
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
		expect(businessCreateDialog).not.toContain('idempotencyKey')
	})

	it('checks unified results before reading business data', () => {
		const components = [
			businessPanel,
			auditLogPanel
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
