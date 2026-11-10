import {installSessionApi,seedAuthenticatedSession} from '../support/auth-session'
describe('应用 API Key 管理', () => {
  const ok = data => ({ statusCode: 200, body: { success: true, code: '00000', data } })
  let keys
  beforeEach(() => {
    // 未声明的接口不得落到本机业务后端。
    cy.intercept('**/api/**', { statusCode: 503, body: { success: false, msg: 'Unmocked test request' } })
    keys = [{ id: '9', maskedKey: 'sk-demo****1234', remark: '同步任务', accessPath: 'GET:/key/**', createTime: '2026-11-04T21:00:00Z' }]
    cy.intercept('GET', '**/system/api/v1/app?*', ok({ records: [{ id: '34', applicationName: '验证应用', code: 'test', accessPath: 'GET:/app/**', createTime: '2026-11-04T21:00:00.000Z' }], total: 1 })).as('apps')
    cy.intercept('GET', '**/system/api/v1/app/34/api-keys?*', request => request.reply(ok({ records: keys, total: keys.length }))).as('keys')
    cy.intercept('GET', '**/system/api/v1/app/34/api-keys/9/secret*', ok({ id: '9', apiKey: 'sk-synthetic-browser-fixture-1234' })).as('secret')
    cy.intercept('GET', '**/system/api/v1/tenants/options*', ok([]))
    cy.intercept('GET', '**/system/api/v1/users?*', ok({ records: [], total: 0 }))
    cy.intercept('GET', '**/system/api/v1/app/validtor/appCode*', ok(true))
    installSessionApi()
    cy.visit('/#/system/application', {
      onBeforeLoad(win) {
        seedAuthenticatedSession(win)
        win.sessionStorage.setItem('USER_INFO', JSON.stringify({ content: { username: 'root', roles: ['ROOT'] }, datetime: 0 }))
        win.sessionStorage.setItem('MENU', JSON.stringify({ content: [{ path: '/system', component: 'Layout', meta: { title: '系统管理' }, children: [{ path: '/system/application', component: 'system/application/index', meta: { title: '应用管理' } }] }], datetime: 0 }))
      }
    })
    cy.wait('@apps')
    cy.window().then(win => {
      const local = new win.Date('2026-11-04T21:00:00.000Z').toLocaleString('sv-SE', { hour12: false })
      cy.contains('td', local).should('exist')
    })
    cy.contains('button', 'APIKEY').click()
    cy.wait('@keys')
  })

  it('displays a masked list, reveals then hides, and clears plaintext on close', () => {
    cy.contains('.el-drawer', 'APIKEY 管理 - 验证应用').should('be.visible')
    cy.contains('.el-drawer', 'sk-demo****1234').should('be.visible')
    cy.contains('.el-drawer', 'GET:/key/**').should('be.visible')
    cy.window().then(win => {
      const local = new win.Date('2026-11-04T21:00:00Z').toLocaleString('sv-SE', { hour12: false })
      expect(new win.Date('2026-11-05T00:50:30.123456789Z').getTime())
        .to.equal(new win.Date('2026-11-05T00:50:30.123Z').getTime())
      cy.contains('.el-drawer', local).should('be.visible')
    })
    cy.get('.el-drawer button[aria-label="显示 API Key"]').click()
    cy.wait('@secret')
    cy.get('.key-icon .el-icon svg').should('have.length', 2)
    cy.contains('.key-value', 'sk-synthetic-browser-fixture-1234').should('be.visible')
    // 实际浏览器几何断言，避免按钮换行回归被文本断言漏掉。
    const assertInline = () => {
      cy.get('.el-drawer:visible .key-actions').each(group => {
        const buttons = group[0].querySelectorAll('button')
        expect(Math.abs(buttons[0].getBoundingClientRect().top - buttons[1].getBoundingClientRect().top)).to.be.lessThan(1)
      })
      cy.get('.el-drawer:visible .key-credential').each(group => {
        const value = group[0].querySelector('.key-value').getBoundingClientRect()
        const actions = group[0].querySelector('.key-actions').getBoundingClientRect()
        expect(Math.abs((value.top + value.bottom) / 2 - (actions.top + actions.bottom) / 2)).to.be.lessThan(1)
        expect(value.right).to.be.at.most(actions.left)
      })
    }
    assertInline()
    cy.window().then(win => {
      Object.defineProperty(win.navigator, 'clipboard', { configurable: true, value: { writeText: cy.stub().resolves().as('clipboard') } })
    })
    cy.get('.el-drawer button[aria-label="复制 API Key"]').click()
    cy.wait('@secret')
    cy.get('@clipboard').should('have.been.calledWith', 'sk-synthetic-browser-fixture-1234')
    cy.screenshot('application-api-key-inline', { capture: 'viewport' })
    cy.viewport(390, 844)
    assertInline()
    cy.screenshot('application-api-key-inline-mobile', { capture: 'viewport' })
    cy.viewport(1280, 720)
    cy.get('.el-drawer button[aria-label="隐藏 API Key"]').click()
    cy.contains('.key-value', 'sk-demo****1234').should('be.visible')
    cy.get('.el-drawer:visible').find('button.el-drawer__close-btn').first().click()
    cy.contains('.el-drawer', 'APIKEY 管理 - 验证应用').should('not.exist')
    cy.contains('button', 'APIKEY').click()
    cy.wait('@keys')
    cy.contains('.key-value', 'sk-demo****1234').should('be.visible')
    cy.contains('.key-value', 'sk-synthetic-browser-fixture-1234').should('not.exist')
    cy.screenshot('application-api-key-list', { capture: 'viewport' })
  })

  it('creates and edits independent paths, then revokes the Key', () => {
    cy.intercept('POST', '**/system/api/v1/app/34/api-keys', request => {
      expect(request.body).to.deep.equal({ remark: '外部系统', accessPath: 'POST:/orders/**\nGET:/orders/**', allowIps: '192.0.2.0/24\n2001:db8::/32' })
      keys.unshift({ id: '10', maskedKey: 'sk-new****5678', ...request.body, createTime: '2026-11-04T22:00:00Z' })
      request.reply(ok({ id: '10', apiKey: 'sk-synthetic-created-fixture-5678' }))
    }).as('create')
    cy.contains('.el-drawer button', '创建 API Key').click()
    cy.get('.el-dialog').filter(':visible').within(() => {
      cy.get('input').type('外部系统')
      cy.get('textarea').eq(0).type('POST:/orders/**{enter}GET:/orders/**')
      cy.get('textarea').eq(1).type('192.0.2.0/24{enter}2001:db8::/32')
      cy.contains('button', /^创建$/).click()
    })
    cy.wait('@create')
    cy.contains('.el-dialog', 'API Key 创建成功').should('be.visible')
    cy.get('.created-key input').should('have.value', 'sk-synthetic-created-fixture-5678')
    cy.contains('.el-dialog button', '确认').click()
    cy.contains('.el-table__row', 'sk-new****5678').should('be.visible')
    cy.intercept('PUT', '**/system/api/v1/app/34/api-keys/10', request => {
      expect(request.body.accessPath).to.equal('')
      expect(request.body.allowIps).to.equal('')
      Object.assign(keys[0], request.body)
      request.reply(ok(null))
    }).as('update')
    cy.contains('.el-table__row', 'sk-new****5678').contains('button', '编辑').click()
    cy.get('.el-dialog').filter(':visible').within(() => {
      cy.get('textarea').eq(0).clear()
      cy.get('textarea').eq(1).clear()
      cy.contains('button', '保存').click()
    })
    cy.wait('@update')
    cy.contains('.el-table__row', 'sk-new****5678').should('contain', '未配置，缓存使用应用 URL')
    cy.intercept('DELETE', '**/system/api/v1/app/34/api-keys/10', request => {
      keys = keys.filter(key => key.id !== '10')
      request.reply(ok(null))
    }).as('remove')
    cy.contains('.el-table__row', 'sk-new****5678').contains('button', '删除').click()
    cy.get('.el-message-box').contains('button', '确定').click()
    cy.wait('@remove')
    cy.contains('.el-table__row', 'sk-new****5678').should('not.exist')
    cy.viewport(390, 844)
    cy.contains('.el-drawer button', '创建 API Key').should('be.visible')
    cy.get('.el-message').should('not.exist')
    cy.screenshot('application-api-key-mobile', { capture: 'viewport' })
  })

  it('explains application paths and rejects invalid contact inline', () => {
    cy.get('.el-drawer:visible').find('button.el-drawer__close-btn').first().click()
    cy.get('.left-panel button').first().click()
    cy.get('.el-dialog:visible').within(() => {
      cy.contains('HTTP方法:/路径').should('be.visible')
      cy.contains('留空表示不限制应用访问路径').should('be.visible')
      cy.get('textarea').eq(0).should('have.attr', 'placeholder', 'GET:/service/api/**\nPOST:/service/api/orders')
      cy.contains('每行一个 IPv4、IPv6 或 CIDR 网段').should('be.visible')
      cy.get('input[placeholder="请输入11位手机号"]').type('1222').blur()
      cy.contains('.el-form-item__error', '请输入有效的11位手机号').should('be.visible')
      cy.contains('button', '保 存').click()
      cy.contains('.el-form-item__error', '请输入有效的11位手机号').should('be.visible')
      cy.get('input[placeholder="请输入11位手机号"]').clear().type('13800138000').blur()
      cy.contains('.el-form-item__error', '请输入有效的11位手机号').should('not.exist')
    })
    cy.screenshot('application-form-path-and-mobile', { capture: 'viewport' })
  })

})
