/* global cy */
let activeWindow
export function installSessionApi() {
    const ok=data=>({success:true,code:'00000',data})
    const cached=key=>JSON.parse(activeWindow.sessionStorage.getItem(key)||'null')?.content
    cy.intercept('GET','**/system/api/v1/users/me*',req=>req.reply(ok({...cached('USER_INFO'),perms:cached('PERMISSIONS')||['*:*:*']})))
    cy.intercept('GET','**/system/api/v1/tenants/options*',req=>req.reply(ok([{value:1,label:'测试租户'}])))
    cy.intercept('GET','**/system/api/v1/menus/routes*',req=>req.reply(ok(cached('MENU')||[])))
}
export function seedAuthenticatedSession(win) {
    activeWindow=win
    win.localStorage.setItem('MACULA_CLOUD_ADMIN_STORAGE_VERSION','3')
    win.sessionStorage.setItem('MACULA_ADMIN_AUTH_GENERATION','1')
    win.sessionStorage.setItem('MACULA_ADMIN_SESSION_V1',JSON.stringify({version:1,sessionId:'synthetic-session',generation:1,
        accessToken:'synthetic-access-token',expiresAt:Date.now()+3600000}))
}
