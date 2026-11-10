import {generateKeyPair,exportJWK,SignJWT} from 'jose'

// 全部凭据均为合成素材，认证 spec 禁用截图/视频。
describe('Admin authorization code + PKCE', {screenshotOnRunFailure:false}, () => {
    const origin=new URL(Cypress.config('baseUrl')).origin
    const issuer=origin+'/mock-iam'
    const ok=data=>({success:true,data})
    let key,jwk,nonce,tokenCalls
    function setup({badState=false,badNonce=false,noTenant=false}={}) {
        tokenCalls=0
        cy.then(async()=>{
            const pair=await generateKeyPair('RS256')
            key=pair.privateKey
            jwk={...await exportJWK(pair.publicKey),alg:'RS256',kid:'synthetic-browser-key'}
        })
        cy.intercept('GET','**/config.js*',{headers:{'content-type':'application/javascript'},body:
            `const APP_CONFIG=${JSON.stringify({OAUTH_CLIENT_ID:'synthetic-public-admin',OAUTH_AUTHORITY:issuer,
                ADMIN_PUBLIC_URL:origin,OAUTH_SCOPE:'openid profile'})};globalThis.APP_CONFIG=APP_CONFIG`})
        cy.intercept('GET','**/mock-iam/.well-known/openid-configuration',{
            issuer,authorization_endpoint:issuer+'/authorize',token_endpoint:issuer+'/token',jwks_uri:issuer+'/keys'})
        cy.intercept('GET','**/mock-iam/keys',req=>req.reply({keys:[jwk]}))
        cy.intercept('GET','**/mock-iam/authorize*',req=>{
            const query=new URL(req.url).searchParams
            nonce=query.get('nonce')
            expect(query.get('code_challenge_method')).to.eq('S256')
            expect(query.get('code_challenge')).to.have.length(43)
            expect(query.get('state')).to.have.length(43)
            expect(query.get('redirect_uri')).to.eq(origin+'/auth/callback')
            const callback=origin+'/auth/callback?code=synthetic-code&state='+encodeURIComponent(badState?'wrong':query.get('state'))
            req.reply({headers:{'content-type':'text/html'},body:`<!doctype html><title>Fixture IAM</title><button onclick="location.assign('${callback}')">Fixture IAM login</button>`})
        }).as('authorize')
        cy.intercept('POST','**/mock-iam/token',async req=>{
            tokenCalls++
            const form=new URLSearchParams(req.body)
            expect(form.get('grant_type')).to.eq('authorization_code')
            expect(form.get('code_verifier')).to.have.length(43)
            expect(form.has('client_secret')).to.eq(false)
            expect(req.headers).not.to.have.property('authorization')
            expect(req.headers).not.to.have.property('tenantid')
            const now=Math.floor(Date.now()/1000)
            const jwt=await new SignJWT({nonce:badNonce?'wrong':nonce}).setProtectedHeader({alg:'RS256',kid:jwk.kid})
                .setIssuer(issuer).setAudience('synthetic-public-admin').setSubject('1:42').setIssuedAt(now).setExpirationTime(now+300).sign(key)
            req.reply({access_token:'synthetic-browser-access',token_type:'Bearer',expires_in:3600,id_token:jwt})
        }).as('token')
        cy.intercept('GET','**/system/api/v1/users/me*',ok({username:'fixture-user',roles:['ADMIN'],perms:['fixture:read']}))
        cy.intercept('GET','**/system/api/v1/tenants/options*',ok(noTenant?[]:[{value:1,label:'Fixture Tenant'}]))
        cy.intercept('GET','**/system/api/v1/menus/routes*',ok([{path:'/fixture',component:'home',meta:{title:'Fixture',roles:['ADMIN']}}]))
        cy.visit('/#/login?returnTo=/fixture')
    }
    function login() {
        cy.contains('button','前往 IAM 登录').click()
        cy.wait('@authorize')
        cy.contains('button','Fixture IAM login').click()
    }
    it('completes physical callback and permissions before entering the requested page; logout stays local',()=>{
        setup();login()
        cy.location('hash').should('eq','#/fixture')
        cy.location('pathname').should('eq','/')
        cy.location('search').should('eq','')
        cy.window().then(win=>{
            expect(win.localStorage.getItem('USER_INFO')).to.eq(null)
            expect(win.document.cookie).not.to.contain('TOKEN=')
            expect(win.sessionStorage.getItem('MACULA_ADMIN_SESSION_V1')).to.contain('synthetic-browser-access')
            expect(win.sessionStorage.getItem('MACULA_ADMIN_AUTH_TRANSACTION_V1')).to.eq(null)
        })
        cy.reload()
        cy.location('hash').should('eq','#/fixture')
        cy.contains('.user-avatar','fixture-user').click()
        cy.get('.el-dropdown-menu:visible').contains('退出登录').click()
        cy.get('.el-message-box').contains('button','退出').click()
        cy.location('hash').should('contain','#/login')
        cy.contains('button','前往 IAM 登录').should('be.visible')
        cy.then(()=>expect(tokenCalls).to.eq(1))
    })
    it('rejects state before exchanging code',()=>{
        setup({badState:true});login()
        cy.contains('登录未完成').should('be.visible')
        cy.then(()=>expect(tokenCalls).to.eq(0))
    })
    it('rejects nonce, does not commit a token, and cannot replay after refresh',()=>{
        setup({badNonce:true});login()
        cy.contains('登录未完成').should('be.visible')
        cy.reload()
        cy.contains('登录未完成').should('be.visible')
        cy.window().then(win=>expect(win.sessionStorage.getItem('MACULA_ADMIN_SESSION_V1')).to.eq(null))
        cy.then(()=>expect(tokenCalls).to.eq(1))
    })
    it('fails closed for empty accessible tenants and offers a manual retry at 390px',()=>{
        cy.viewport(390,844)
        setup({noTenant:true});login()
        cy.contains('登录未完成').should('be.visible')
        cy.contains('button','返回登录').click()
        cy.contains('button','前往 IAM 登录').should('be.visible')
        cy.window().then(win=>{
            expect(win.sessionStorage.getItem('MACULA_ADMIN_SESSION_V1')).to.eq(null)
            expect(win.document.documentElement.scrollWidth).to.be.at.most(390)
        })
    })
})
