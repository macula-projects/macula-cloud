describe('Admin unified login',()=>{
    it('shows IAM login without password or phone forms',()=>{
        cy.visit('/')
        cy.location('hash').should('contain','#/login')
        cy.contains('button','前往 IAM 登录').should('be.visible')
        cy.get('input[type=password]').should('not.exist')
    })
    for(const path of ['/user_register','/reset_password']) {
        it('redirects '+path+' to unified login',()=>{
            cy.visit('/#'+path)
            cy.location('hash').should('eq','#/login')
        })
    }
})
