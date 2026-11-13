import {describe,it,expect} from 'vitest';
import {externalCodeAllowed,browserSourceAllowed,browserEnvironmentLabel,sourceSelectionIssue} from '@/utils/identitySourceCompatibility';
describe('应用架构与身份源能力矩阵',()=>{
  it('Web不接直接Code，端内接JSAPI，原生接SDK，小程序固定平台',()=>{
    for (const applicationType of ['WEB_SERVER','SPA']) {
      expect(externalCodeAllowed({applicationType},'WECHAT_OPEN_APP_CODE')).toBe(false);
      expect(externalCodeAllowed({applicationType},'DINGTALK_WEBVIEW_CODE')).toBe(false);
      expect(externalCodeAllowed({applicationType},'WECHAT_MINIAPP_LOGIN_CODE')).toBe(false);
    }
    expect(externalCodeAllowed({applicationType:'NATIVE'},'WECHAT_OPEN_APP_CODE')).toBe(true);
    expect(externalCodeAllowed({applicationType:'NATIVE'},'FEISHU_WEBVIEW_CODE')).toBe(false);
    expect(externalCodeAllowed({applicationType:'MINIAPP',miniappPlatform:'WECHAT'},'WECHAT_MINIAPP_PHONE_CODE')).toBe(true);
    expect(externalCodeAllowed({applicationType:'MINIAPP',miniappPlatform:'WECHAT'},'WECOM_MINIAPP_CODE')).toBe(false);
    expect(externalCodeAllowed({applicationType:'SERVICE'},'WECOM_APP_CODE')).toBe(false);
    expect(externalCodeAllowed({applicationType:'NATIVE'},'UNKNOWN_APP_CODE')).toBe(false);
  });
  it('采用服务端能力收窄，不把公众号端内能力提供给原生客户端',()=>{
    const official={browser:true,scene:'WEBVIEW',browserApplicationTypes:['WEB_SERVER','SPA'],browserEnvironments:['WECHAT_WEBVIEW'],capabilities:[{browser:true,loginCapability:'WECHAT_OFFICIAL_ACCOUNT',applicationTypes:['WEB_SERVER','SPA'],webLoginModes:['IN_APP']}]};
    expect(browserSourceAllowed({applicationType:'NATIVE'},official)).toBe(false);
    expect(browserSourceAllowed({applicationType:'SPA'},official)).toBe(false);
    expect(browserSourceAllowed({applicationType:'MINIAPP'},official)).toBe(false);
    expect(browserSourceAllowed({applicationType:'SERVICE'},official)).toBe(false);
    expect(browserSourceAllowed({applicationType:'NATIVE'},{browser:true,scene:'BROWSER'})).toBe(false);
    expect(browserEnvironmentLabel(official)).toBe('微信内网页');
    expect(externalCodeAllowed({applicationType:'NATIVE'},'WECHAT_OPEN_APP_CODE',
      {capabilities:[{codeType:'WECHAT_OPEN_APP_CODE',applicationTypes:[]}]})).toBe(false);
  });
});


it('uses real enabled capabilities without treating shared credentials as every login mode', () => {
  const client={applicationType:'NATIVE'};
  const source={browser:true,capabilities:[{codeType:'WECOM_APP_CODE',available:false,applicationTypes:['NATIVE']} ]};
  expect(externalCodeAllowed(client,'WECOM_APP_CODE',source)).toBe(false);
});

it('端内JSAPI只给端内应用且移动SDK单选，小程序双Code同源',()=>{
 expect(externalCodeAllowed({applicationType:'SPA',webLoginMode:'IN_APP'},'FEISHU_WEBVIEW_CODE',{capabilities:[{codeType:'FEISHU_WEBVIEW_CODE',applicationTypes:['WEB_SERVER','SPA'],webLoginModes:['IN_APP']}]})).toBe(true);
 expect(externalCodeAllowed({applicationType:'SPA',webLoginMode:'IN_APP'},'FEISHU_APP_CODE')).toBe(false);
 const a={source:{id:1},capability:'WECHAT_MINIAPP_LOGIN_CODE'},b={source:{id:1},capability:'WECHAT_MINIAPP_PHONE_CODE'};
 expect(sourceSelectionIssue({applicationType:'MINIAPP'},[a,b])).toBe('');
 expect(sourceSelectionIssue({applicationType:'MINIAPP'},[a,{...b,source:{id:2}}])).toBeTruthy();
 expect(sourceSelectionIssue({applicationType:'NATIVE'},[a,b])).toBeTruthy();
});

it('旧目录缺少模式元数据时不猜端内能力，旧独立类型被拒绝',()=>{
 const source={browser:true,capabilities:[{browser:true,loginCapability:'WECOM_WEBVIEW',applicationTypes:['WEB_SERVER','SPA']}]};
 expect(browserSourceAllowed({applicationType:'SPA',webLoginMode:'IN_APP'},source)).toBe(false);
 expect(browserSourceAllowed({applicationType:'IN_APP'},source)).toBe(false);
 expect(externalCodeAllowed({applicationType:'SPA',webLoginMode:'IN_APP'},'FEISHU_WEBVIEW_CODE',{})).toBe(false);
});
