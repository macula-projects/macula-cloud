describe('TinyID management', () => {
	const ok = data => ({statusCode: 200, body: {success: true, code: '00000', data}})

	beforeEach(() => {
		cy.setCookie('TOKEN', 'root-test-token')
		cy.intercept('GET', '**/tinyid/api/v1/admin/apps*', ok({records: [], total: 0}))
		cy.intercept('GET', '**/tinyid/api/v1/admin/businesses*', ok({records: [], total: 0}))
	})

	function openPage() {
		cy.visit('/#/tinyid/management', {
			onBeforeLoad(win) {
				win.localStorage.setItem('USER_INFO', JSON.stringify({content: {username: 'root', roles: ['ROOT']}, datetime: 0}))
				win.localStorage.setItem('MENU', JSON.stringify({content: [{
					path: '/system',
					component: 'Layout',
					meta: {title: '系统管理'},
					children: [{
						path: '/tinyid/management',
						component: 'tinyid/management/index',
						meta: {title: 'ID管理'}
					}]
				}], datetime: 0}))
			}
		})
	}

	it('shows aggregate businesses and read-only datasource remainders', () => {
		cy.intercept('GET', '**/tinyid/api/v1/admin/apps*', ok({records: [], total: 0}))
		cy.intercept('GET', '**/tinyid/api/v1/admin/businesses*', ok({records: [{
			bizType: 'order', step: 100, delta: 10, consistencyStatus: 'COMPLETE', dataSources: [
				{id: 1, sequence: 0, dataSourceKey: 'master', healthy: true, remainder: 0, maxId: 0, version: 0},
				{id: 2, sequence: 1, dataSourceKey: 'replica', healthy: true, remainder: 1, maxId: 0, version: 0}
			]
		}], total: 1})).as('businesses')
		cy.intercept('DELETE', '**/tinyid/api/v1/admin/businesses/order', ok(null)).as('deleteBusiness')

		openPage()
		cy.wait('@businesses')
		cy.get('.tinyid-management .el-loading-mask').should('not.be.visible')
		cy.contains('第一步：').should('be.visible')
		cy.contains('order').should('be.visible')
		cy.get('.el-table__expand-icon').click()
		cy.contains('replica').should('be.visible')
		cy.contains('remainder（只读）').should('be.visible')
		cy.contains('button', '删除业务').should('not.be.disabled').click()
		cy.contains('.el-message-box', '删除发号业务').within(() => cy.contains('button', '删除').click())
		cy.wait('@deleteBusiness')
	})

	it('shows the complete application token and allows deleting the application', () => {
		cy.intercept('GET', '**/tinyid/api/v1/admin/apps*', ok({records: [{
			appId: 1, remark: '订单应用', token: 'complete-root-visible-token', bizTypes: ['order']
		}], total: 1}))
		cy.intercept('DELETE', '**/tinyid/api/v1/admin/apps/1', ok(null)).as('deleteApplication')

		openPage()
		cy.contains('.el-tabs__item', '接入应用').click()
		cy.contains('complete-root-visible-token').should('be.visible')
		cy.contains('button', '删除应用').click()
		cy.contains('.el-message-box', '删除接入应用').within(() => cy.contains('button', '删除').click())
		cy.wait('@deleteApplication')
	})

	it('requires a completed business before creating an application', () => {
		cy.intercept('GET', '**/tinyid/api/v1/admin/apps*', ok({records: [], total: 0}))
		cy.intercept('GET', '**/tinyid/api/v1/admin/businesses*', ok({records: [], total: 0}))

		openPage()
		cy.contains('.el-tabs__item', '接入应用').click()
		cy.contains('新增接入应用').click()
		cy.contains('请先在“发号业务”完成至少一个 biz_type 配置').should('be.visible')
		cy.contains('.el-dialog', '新增接入应用').should('not.exist')
	})

	it('creates a business with server-assigned datasource fields and reports capacity failures', () => {
		cy.intercept('GET', '**/tinyid/api/v1/admin/apps*', ok({records: [], total: 0}))
		cy.intercept('GET', '**/tinyid/api/v1/admin/businesses*', ok({records: [], total: 0}))
		cy.intercept('POST', '**/tinyid/api/v1/admin/businesses', request => {
			expect(request.body).to.include({bizType: 'capacity-test', step: 100, delta: 10})
			expect(request.body).not.to.have.property('idempotencyKey')
			expect(request.body).not.to.have.property('dataSourceKey')
			expect(request.body).not.to.have.property('remainder')
			request.reply({statusCode: 500, body: {success: false, code: 'B0001', msg: 'delta 容量不足，无法覆盖全部数据源'}})
		}).as('createBusiness')

		openPage()
		cy.contains('新增业务').click()
		cy.contains('.el-dialog', '新增发号业务').within(() => {
			cy.contains('预留数据库实例容量，默认 10').should('be.visible')
			cy.get('input').filter('[maxlength="63"]').type('capacity-test')
			cy.contains('在全部数据源创建').click()
		})
		cy.wait('@createBusiness')
		cy.contains('.el-message', 'delta 容量不足，无法覆盖全部数据源').should('be.visible')
		cy.contains('.el-dialog', '新增发号业务').should('be.visible')
	})

	it('reads created tokens from Result data and keeps failed deletions visible', () => {
		cy.intercept('GET', '**/tinyid/api/v1/admin/businesses*', ok({records: [{
			bizType: 'order', consistencyStatus: 'COMPLETE', dataSources: []
		}], total: 1}))
		cy.intercept('GET', '**/tinyid/api/v1/admin/apps*', ok({records: [{
			appId: 1, remark: '订单应用', token: 'test-visible-token', bizTypes: ['order']
		}], total: 1}))
		cy.intercept('POST', '**/tinyid/api/v1/admin/apps', ok({token: 'test-created-token'})).as('createApp')
		cy.intercept('DELETE', '**/tinyid/api/v1/admin/apps/1', {
			statusCode: 500, body: {success: false, code: 'B0001', msg: '删除失败'}
		}).as('deleteFailed')
		openPage()
		cy.contains('.el-tabs__item', '接入应用').click()
		cy.contains('button', '删除应用').click()
		cy.contains('.el-message-box', '删除接入应用').within(() => cy.contains('button', '删除').click())
		cy.wait('@deleteFailed')
		cy.contains('.el-message', '删除失败').should('be.visible')
		cy.contains('test-visible-token').should('be.visible')
		cy.contains('接入应用已删除').should('not.exist')
		cy.contains('新增接入应用').click()
		cy.contains('.el-dialog', '新增接入应用').within(() => {
			cy.get('input[maxlength="255"]').type('新应用')
			cy.get('.el-select').click()
		})
		cy.get('.el-select-dropdown:visible').contains('order').click()
		cy.contains('.el-dialog', '新增接入应用').contains('创建并展示 Token').click()
		cy.wait('@createApp')
		cy.contains('.el-dialog', '应用已创建').find('input').should('have.value', 'test-created-token')
	})

	it('shows sanitized success and failure audit records', () => {
		cy.intercept('GET', '**/tinyid/api/v1/admin/apps*', ok({records: [], total: 0}))
		cy.intercept('GET', '**/tinyid/api/v1/admin/businesses*', ok({records: [], total: 0}))
		cy.intercept('GET', '**/tinyid/api/v1/admin/audit-logs*', ok({records: [
			{operator: 'root', action: '创建发号业务', requestMethod: 'POST', requestUri: '/api/v1/admin/businesses', success: true},
			{operator: 'SYSTEM', action: 'TinyID 数据源扩容补齐', requestMethod: 'SYSTEM', requestUri: 'startup://tinyid/datasource-reconcile', success: false, errorSummary: 'TinyID datasource reconciliation failed'}
		], total: 2})).as('auditLogs')

		openPage()
		cy.contains('.el-tabs__item', '审计日志').click()
		cy.wait('@auditLogs')
		cy.contains('创建发号业务').should('be.visible')
		cy.contains('TinyID 数据源扩容补齐').should('be.visible')
		cy.contains('TinyID datasource reconciliation failed').should('be.visible')
		cy.contains('runtime-test-token').should('not.exist')
	})
})
