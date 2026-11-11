// 由HostedEmailLoginFlowTest生成真实Thymeleaf页面；通过IAM_PREVIEW_URL显式提供隔离预览地址。
// SMTP、账号核验及协议端点由后端测试验证，本用例只验证浏览器交互。
const previewUrl = Cypress.expose('IAM_PREVIEW_URL');
(previewUrl ? describe : describe.skip)("IAM 托管登录页面", () => {
  const url = previewUrl;
  for (const width of [320, 390, 1440]) {
    it(`${width}px图标单行、可访问且不溢出`, () => {
      cy.viewport(width, 1000);
      cy.visit(url);
      cy.get('.external-icons a').should('have.length', 5).then(links => {
        const boxes = [...links].map(link => link.getBoundingClientRect());
        expect(new Set(boxes.map(box => Math.round(box.y))).size).to.eq(1);
        boxes.forEach(box => { expect(box.width).to.be.at.least(44); expect(box.height).to.be.at.least(44); });
        [...links].forEach(link => {
          expect(link.getAttribute('href')).to.match(/^\/login\/(github|wecom|feishu|dingtalk|wechat)$/);
          expect(link.getAttribute('aria-label')).to.eq(link.title);
          expect(link.querySelector('img').complete).to.eq(true);
          expect(link.querySelector('img').naturalWidth).to.be.greaterThan(0);
        });
      });
      cy.document().then(doc => expect(doc.documentElement.scrollWidth).to.be.at.most(width));
      cy.get('.external-icons a').first().focus().should('have.focus');
      cy.get('[data-tab="email-login"]').click();
      cy.get('#email-login').should('be.visible');
      cy.get('#password-login').should('not.be.visible');
      cy.screenshot(`iam-login-${width}`);
    });
  }
  it('邮箱发码携带CSRF、倒计时并防止重复发送', () => {
    cy.clock();
    cy.visit(url);
    cy.get('[data-tab="email-login"]').click();
    cy.get('#email').type('alice@example.test');
    cy.intercept('POST', '**/api/v1/email-code', req => {
      const body = new URLSearchParams(req.body);
      expect(body.get('email')).to.eq('alice@example.test');
      expect(body.get('_csrf')).to.eq('fixture-csrf');
      expect(body.has('phone')).to.eq(false);
      req.reply({statusCode:200, body:{status:'submitted'}});
    }).as('send');
    cy.get('#email-login [data-send-code]').click();
    cy.wait('@send');
    cy.get('#email-login [data-send-code]').should('be.disabled').and('contain','60');
    cy.tick(60000);
    cy.get('#email-login [data-send-code]').should('be.enabled').and('contain','获取验证码');
    cy.get('@send.all').should('have.length',1);
  });
  it('投递失败可重试，页签支持键盘，提交按钮阻止重复提交', () => {
    cy.visit(url);
    cy.get('[data-tab="password-login"]').focus().trigger('keydown',{key:'End'});
    cy.get('[data-tab="email-login"]').should('have.focus').and('have.attr','aria-selected','true');
    cy.get('#email').type('alice@example.test');
    cy.intercept('POST', '**/api/v1/email-code', {statusCode:503}).as('send');
    cy.get('#email-login [data-send-code]').click();
    cy.wait('@send');
    cy.get('#email-login [role="alert"]').should('contain','暂时无法发送');
    cy.get('#email-login [data-send-code]').should('be.enabled');
    cy.get('#email-code').type('123456');
    cy.get('#email-login').then(form => {
      form[0].addEventListener('submit', event => event.preventDefault());
      form[0].dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
    });
    cy.get('#email-login [type="submit"]').should('be.disabled');
  });
});
