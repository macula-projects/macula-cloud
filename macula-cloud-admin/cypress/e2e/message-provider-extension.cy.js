import { installSessionApi, seedAuthenticatedSession } from '../support/auth-session'

const ok = data => ({ success: true, code: '00000', data })
const smsProvider = { channel: 'SMS', providerType: 'CUSTOM_HTTP', enabled: true,
  endpoint: 'https://sms.example.test/send', sender: 'fixture-sign', credentialConfigured: true, authHeadersConfigured: true }
const smsTemplates = [{ templateKey: 'SMS_LOGIN', channel: 'SMS', providerType: 'CUSTOM_HTTP',
  languageCode: 'zh-CN', providerTemplateCode: 'LOGIN_001', enabled: true, variables: ['code'] }]

function open() {
  installSessionApi()
  cy.intercept('GET', '**/message-settings/templates/MAIL*', ok([])).as('mailTemplates')
  cy.intercept('GET', '**/message-settings/providers/MAIL*', ok({ channel: 'MAIL', providerType: 'CUSTOM', enabled: true,
    endpoint: 'smtp.example.test', port: 465, securityMode: 'SSL', account: 'fixture-user', sender: 'sender@example.test',
    credentialConfigured: true })).as('mailProvider')
  cy.intercept('GET', '**/message-settings/providers/SMS*', ok(smsProvider)).as('smsProvider')
  cy.intercept('GET', '**/message-settings/templates/SMS*', ok(smsTemplates)).as('smsTemplates')
  cy.visit('/#/system/message-settings', { onBeforeLoad(win) {
    seedAuthenticatedSession(win)
    win.sessionStorage.setItem('USER_INFO', JSON.stringify({ content: { username: 'root', roles: ['ROOT'] }, datetime: 0 }))
    win.sessionStorage.setItem('MENU', JSON.stringify({ content: [{ path: '/system', component: 'Layout', meta: { title: '系统管理' },
      children: [{ path: '/system/message-settings', component: 'system/messageSettings/index', meta: { title: '消息设置' } }] }], datetime: 0 }))
  } })
  cy.wait('@mailTemplates')
}
function selectProvider(label) {
  cy.get('.message-page .el-select').first().click()
  cy.get('.el-select-dropdown:visible').contains('.el-select-dropdown__item', label).click()
}
function openSms() {
  open()
  cy.contains('.el-tabs__item', '短信服务').click()
  cy.wait(['@smsProvider', '@smsTemplates'])
}

describe('消息渠道扩展', () => {
  it('配置自建 Exchange 并显示个人邮箱选项和授权码提示', () => {
    open()
    cy.contains('.el-tabs__item', '邮件服务').click()
    cy.wait('@mailProvider')
    selectProvider('Exchange（企业自建）')
    cy.contains('.el-form-item', '端口').find('input').should('have.value', '587')
    cy.contains('.el-radio', 'STARTTLS').should('have.class', 'is-checked')
    cy.contains('.el-form-item', 'SMTP 服务器').find('input').type('exchange.example.test')
    cy.contains('.el-form-item', '用户名 / 邮箱').find('input').type('fixture-user')
    cy.contains('.el-form-item', '密码').find('input').type('fixture-password')
    cy.intercept('PUT', '**/message-settings/providers/MAIL', req => {
      expect(req.body).to.include({ providerType: 'EXCHANGE', endpoint: 'exchange.example.test', port: 587, securityMode: 'STARTTLS' })
      expect(req.body).not.to.have.property('credentialConfigured')
      expect(req.body).not.to.have.property('channel')
      req.reply(ok({ ...req.body, credential: undefined, credentialConfigured: true }))
    }).as('saveMail')
    cy.contains('.message-page button', '保存').first().click()
    cy.wait('@saveMail')
    selectProvider('163 免费邮箱')
    cy.contains('使用客户端授权码').should('be.visible')
    selectProvider('QQ 邮箱')
    cy.contains('使用客户端授权码').should('be.visible')
  })

  it('保留或整体替换认证 Header，并继续使用已有模板和测试发送', () => {
    openSms()
    cy.contains('全部留空保持原值').should('be.visible')
    cy.intercept('PUT', '**/message-settings/providers/SMS', req => {
      expect(req.body).not.to.have.property('credential')
      expect(req.body).not.to.have.property('account')
      expect(req.body).not.to.have.property('authHeadersConfigured')
      req.reply(ok(smsProvider))
    }).as('saveSms')
    cy.contains('.message-page button', '保存').first().click()
    cy.wait('@saveSms').its('request.body.authHeaders').should('deep.equal', {})
    cy.contains('button', '添加 Header').click()
    cy.get('input[data-testid="sms-header-name"], [data-testid="sms-header-name"] input').type('Authorization')
    cy.get('input[data-testid="sms-header-value"], [data-testid="sms-header-value"] input').type('Bearer fixture-new')
    cy.contains('.message-page button', '保存').first().click()
    cy.wait('@saveSms').its('request.body.authHeaders').should('deep.equal', { Authorization: 'Bearer fixture-new' })
    cy.get('[data-testid="sms-header-value"]').should('not.exist')
    cy.intercept('PUT', '**/message-settings/templates/SMS/SMS_LOGIN', req => {
      expect(req.body.providerTemplateCode).to.equal('LOGIN_002')
      expect(req.body.providerType).to.equal('CUSTOM_HTTP')
      req.reply(ok({}))
    }).as('saveTemplate')
    cy.get('.el-table input').clear().type('LOGIN_002')
    cy.get('.el-table').contains('button', '保存').click()
    cy.wait('@saveTemplate')
    cy.intercept('POST', '**/message-settings/test', req => {
      expect(req.body).to.include({ scene: 'SMS_LOGIN', languageCode: 'zh-CN' })
      expect(req.body).not.to.have.property('endpoint')
      expect(req.body).not.to.have.property('authHeaders')
      req.reply(ok({ status: 'submitted' }))
    }).as('testSms')
    cy.get('.el-table').contains('button', '测试').click()
    cy.get('.el-dialog:visible input').type('13800000000')
    cy.get('.el-dialog:visible').contains('button', '提交').click()
    cy.wait('@testSms')
  })

  it('拒绝重复 Header，并在切换供应商时清除编辑中的秘密', () => {
    openSms()
    for (const name of ['Authorization', 'authorization']) {
      cy.contains('button', '添加 Header').click()
      cy.get('input[data-testid="sms-header-name"], [data-testid="sms-header-name"] input').last().type(name)
      cy.get('input[data-testid="sms-header-value"], [data-testid="sms-header-value"] input').last().type('fixture-value')
    }
    cy.contains('.message-page button', '保存').first().click()
    cy.contains('Header 名称、值或重复配置无效').should('be.visible')
    selectProvider('阿里云')
    cy.get('[data-testid="sms-header-value"]').should('not.exist')
    selectProvider('自定义短信服务')
    cy.get('[data-testid="sms-header-value"]').should('not.exist')
    cy.get('input[data-testid="sms-post-url"], [data-testid="sms-post-url"] input').should('have.value', '')
  })

  it('配置读取失败时显示错误并可重试', () => {
    open()
    cy.intercept({ method: 'GET', url: '**/message-settings/providers/SMS*', times: 1 },
      { statusCode: 500, body: { success: false, msg: 'fixture-load-failed' } }).as('loadFailure')
    cy.contains('.el-tabs__item', '短信服务').click()
    cy.wait('@loadFailure')
    cy.get('.message-page > .el-alert').should('be.visible')
    cy.get('.message-page > .el-alert').contains('button', '重新加载').click()
    cy.wait('@smsProvider')
    cy.contains('全部留空保持原值').should('be.visible')
  })

  it('停用使用已保存配置，不受未完成的 Header 和地址编辑阻塞', () => {
    openSms()
    cy.contains('button', '添加 Header').click()
    cy.get('input[data-testid="sms-header-name"], [data-testid="sms-header-name"] input').type('X-Incomplete')
    cy.get('input[data-testid="sms-post-url"], [data-testid="sms-post-url"] input').clear().type('not-a-url')
    cy.intercept('PUT', '**/message-settings/providers/SMS', req => {
      expect(req.body).to.deep.equal({ providerType: 'CUSTOM_HTTP', enabled: false,
        sender: 'fixture-sign', endpoint: 'https://sms.example.test/send' })
      req.reply(ok({ ...smsProvider, enabled: false }))
    }).as('disableSms')
    cy.get('.message-page .el-switch').first().click()
    cy.contains('.message-page button', '保存').first().click()
    cy.wait('@disableSms')
    cy.get('.message-page .el-switch').first().should('not.have.class', 'is-checked')
  })
})
