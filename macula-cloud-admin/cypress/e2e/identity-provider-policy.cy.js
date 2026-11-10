import {installSessionApi, seedAuthenticatedSession} from '../support/auth-session';
beforeEach(() => installSessionApi());
const ok = data => ({success:true, code:'00000', data});
const fields = [{key:'corpId',label:'企业 ID',required:true}, {key:'agentId',label:'AgentId',required:true},
  {key:'appSecret',label:'Secret',required:true,secret:true}];
const catalog = {
  categories:[{code:'enterprise',label:'企业身份提供商'}],
  providers:[{code:'WECOM',label:'企业微信',category:'enterprise'}, {code:'FEISHU',label:'飞书',category:'enterprise'}],
  sources:[{code:'WECOM_WEB',providerType:'WECOM',label:'企业微信网页',scene:'BROWSER',fields,bindingRules:[],
    flows:[{value:'WECOM_WEB_V1',label:'WECOM_WEB_V1',available:true}],directoryPolicies:['IDENTITY_ONLY']},
  {code:'WECOM_SDK',providerType:'WECOM',label:'企微 SDK',scene:'APP',fields,bindingRules:[],
    flows:[{value:'WECOM_SDK_V1',label:'企微 SDK v1',available:true}],
    directoryPolicies:['IDENTITY_ONLY','OPTIONAL','REQUIRED','BACKGROUND']},
  {code:'FEISHU_APPLINK',providerType:'FEISHU',label:'飞书 App Link',scene:'APP',
    fields:[{key:'appId',label:'App ID',required:true},{key:'appSecret',label:'Secret',required:true,secret:true}],bindingRules:[],
    flows:[{value:'FEISHU_APPLINK_V3',label:'App Link v3',available:false,providerPkce:true,reason:'官方合同待核实'}],
    directoryPolicies:['IDENTITY_ONLY','REQUIRED']}],
};
function open(overrides = {}, edit = true) {
  let row = {id:7,name:'Corp fixture',providerType:'WECOM',sourceType:'WECOM_SDK',identityFlow:'WECOM_SDK_V1',
    directoryPolicy:'BACKGROUND',organizationId:'fixture-corp',oauthRedirectUri:null,
    version:3,enabled:true,visible:true,autoBindEnabled:false,bindingRule:null,
    configuredSecrets:{appSecret:true},config:{corpId:'fixture-corp',agentId:'1001'},...overrides};
  cy.intercept('GET','**/system/api/v1/identity-providers*',req=>req.reply(ok({records:[row],total:1,current:1,size:10})));
  cy.intercept('GET','**/system/api/v1/identity-providers/catalog*',ok(catalog)).as('catalog');
  cy.intercept('PUT','**/system/api/v1/identity-providers/*',req=>{
    row = {...row,...req.body};req.reply(ok(true));
  }).as('updateSource');
  cy.visit('/#/system/identity-provider',{onBeforeLoad(win){
    seedAuthenticatedSession(win);
    for (const [key,content] of Object.entries({USER_INFO:{username:'root',roles:['ROOT']},MENU:[{
      path:'/system',component:'Layout',meta:{title:'系统管理'},children:[{
        path:'/system/identity-provider',component:'system/identityProvider/index',meta:{title:'身份提供商'},
      }],
    }]})) win.sessionStorage.setItem(key,JSON.stringify({content,datetime:0}));
  }});
  cy.wait('@catalog');
  if (edit) cy.contains('.el-table__row','Corp fixture').contains('button','修改').click();
}
describe('身份源Flow与组织策略',()=>{
  for (const width of [1280,390]) {
    it(`preserves fixed registration policy and visible footer at ${width}px`,()=>{
      cy.viewport(width,844);open();
      cy.contains('.el-drawer .el-form-item','接入类型').find('.el-radio').should('have.class','is-disabled');
      cy.contains('.el-drawer .el-form-item','固定组织标识').find('input').should('be.disabled').and('have.value','fixture-corp');
      cy.contains('.el-drawer .el-form-item','通讯录策略').should('not.exist');
      cy.contains('.el-drawer .el-form-item','平台接口版本').should('not.exist');
      cy.contains('该来源原有的企业成员准入要求继续执行').should('be.visible');
      cy.contains('.el-drawer__footer button','确定').should('be.visible').then(button=>{
        expect(button[0].getBoundingClientRect().bottom).to.be.at.most(844);
      }).click();
      cy.wait('@updateSource').then(({request})=>{
        expect(request.body).not.to.have.property('identityFlow');
        expect(request.body).not.to.have.property('directoryPolicy');
        expect(request.body.organizationId).to.equal('fixture-corp');
        expect(request.body.config).not.to.have.property('appSecret');
      });
    });
  }
  it('keeps an unverified source disabled and refuses to enable it',()=>{
    open({providerType:'FEISHU',sourceType:'FEISHU_APPLINK',identityFlow:'FEISHU_APPLINK_V3',directoryPolicy:'IDENTITY_ONLY',
      organizationId:null,enabled:false,config:{appId:'fixture-app'}});
    cy.contains('该接入方式的客户端合同尚待核实').should('be.visible');
    cy.contains('.el-drawer .el-form-item','启用').find('.el-switch').click();
    cy.contains('.el-drawer__footer button','确定').click();
    cy.contains('.el-message','该接入方式尚待核实，只能禁用保存').should('be.visible');
    cy.contains('.el-drawer .el-form-item','启用').find('.el-switch').click();
    cy.contains('.el-drawer__footer button','确定').click();
    cy.wait('@updateSource').its('request.body').should('include',{enabled:false});
  });
});


describe('身份源创建布局与错误提示', () => {
  for (const width of [1280, 390]) {
    it(`keeps multiline labels clear of the next field at ${width}px`, () => {
      cy.viewport(width, 844);
      open({}, false);
      cy.contains('button', '添加认证源').click();
      cy.contains('.el-drawer .el-radio', '企业微信网页').click();
      cy.contains('.el-drawer', '同一自建应用覆盖浏览器扫码与企业微信端内网页授权').should('be.visible');
      cy.contains('.el-drawer .el-form-item', '平台接口版本').should('not.exist');
      cy.get('.el-drawer__body').then(body => {
        expect(body[0].scrollWidth).to.be.at.most(body[0].clientWidth + 1);
      });
      cy.screenshot(`identity-source-create-${width}px`, {capture:'viewport'});
      cy.contains('.el-drawer .el-form-item__label', /^Secret$/).parent().find('input').type('fixture-secret');
      cy.contains('.el-drawer .el-form-item__label', /^企业 ID$/).parent().find('input').type('fixture-corp');
      cy.contains('.el-drawer .el-form-item__label', /^AgentId$/).parent().find('input').type('1001');
      cy.contains('.el-drawer .el-form-item__label', /^认证源名称$/).parent().find('input').type('New Corp');
      cy.intercept('POST', '**/system/api/v1/identity-providers', ok({id:8})).as('createSource');
      cy.intercept('GET', '**/system/api/v1/identity-providers?*', ok({records:[{
        id:8,name:'New Corp',providerType:'WECOM',sourceType:'WECOM_WEB',config:{},enabled:true,visible:true,
      }],total:1})).as('refreshedSources');
      cy.contains('.el-drawer__footer button', '确定').click();
      cy.wait('@createSource').its('request.body').should('include', {sourceType:'WECOM_WEB'});
      cy.wait('@refreshedSources');
      cy.contains('.el-table__row', 'New Corp').should('be.visible');
      cy.contains('数据格式错误').should('not.exist');
    });
  }
  it('shows the service error instead of a generic data format failure', () => {
    open();
    cy.contains('.el-drawer__footer button', '取消').click();
    cy.intercept('GET', '**/system/api/v1/identity-providers?*', {
      success:false,code:'A0409',msg:'身份源附加密钥不是有效 JSON 对象',data:null,
    }).as('failedSources');
    cy.reload();
    cy.wait('@failedSources');
    cy.contains('.el-empty__description', '身份源附加密钥不是有效 JSON 对象').should('be.visible');
    cy.contains('数据格式错误').should('not.exist');
  });
  for (const width of [1440,390]) {
    it(`edits multiple Feishu mappings without a source callback at ${width}px`, () => {
      const source=catalog.sources.find(item=>item.code==='FEISHU_APPLINK');
      const previous={...source};
      source.flows=[{value:'FEISHU_APPLINK_V3',available:true,providerPkce:true}];
      source.bindingMappings=[{sourceField:'mobile',sourceLabel:'手机号',targetField:'mobile',targetLabel:'手机号'},
        {sourceField:'email',sourceLabel:'邮箱',targetField:'email',targetLabel:'邮箱'}];
      cy.viewport(width,900);
      open({providerType:'FEISHU',sourceType:'FEISHU_APPLINK',identityFlow:'FEISHU_APPLINK_V3',
        directoryPolicy:'IDENTITY_ONLY',organizationId:'tenant-fixture',config:{appId:'fixture-app'},
        oauthRedirectUri:' http://obsolete.example/callback',autoBindEnabled:true,
        bindingMappings:[{sourceField:'mobile',targetField:'mobile'},{sourceField:'email',targetField:'email'}]});
      cy.get('[data-binding-rule]').should('have.length',2).first().scrollIntoView();
      cy.get('[data-binding-rule]').each(row=>{
        const controls=[...row[0].querySelectorAll('.el-select,.binding-remove')].map(item=>item.getBoundingClientRect());
        expect(Math.max(...controls.map(item=>item.top))-Math.min(...controls.map(item=>item.top))).to.be.lessThan(3);
        expect(controls[controls.length-1].right).to.be.at.most(width);
      });
      cy.screenshot(`identity-source-multiple-bindings-${width}px`);
      cy.get('.el-drawer input[placeholder="https://app.example/callback"]').should('not.exist');
      cy.contains('.el-drawer__footer button','确定').click();
      cy.wait('@updateSource').then(({request})=>{
        expect(request.body.bindingMappings).to.deep.equal([{sourceField:'mobile',targetField:'mobile'},{sourceField:'email',targetField:'email'}]);
        expect(request.body).not.to.have.property('oauthRedirectUri');
        expect(request.body).not.to.have.property('bindingSourceField');
      });
      cy.contains('.el-table__row','Corp fixture').contains('button','修改').click({force:true});
      cy.get('[data-binding-rule]').should('have.length',2);
      cy.contains('[data-binding-rule] button','移除规则').first().click({force:true});
      cy.get('[data-binding-rule]').should('have.length',1);
      cy.contains('.el-drawer button','添加绑定规则').click({force:true});
      cy.get('[data-binding-rule]').should('have.length',2);
      cy.contains('.el-drawer__footer button','确定').click();
      cy.contains('.el-message','请选择受支持的源字段和IAM字段').should('be.visible');
      cy.get('[data-binding-rule]').should('have.length',2);
      cy.then(()=>Object.assign(source,previous));
    });
  }

});
