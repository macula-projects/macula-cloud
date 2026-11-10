// 目录元数据由System共享白名单生成；旧目录回退只识别已知Code，不能按任意后缀猜能力。
const codeScenes = Object.freeze({
  FEISHU_APP_CODE:'APP', WECOM_APP_CODE:'APP', DINGTALK_APP_CODE:'APP',
  WECHAT_OPEN_APP_CODE:'APP', ALIYUN_MOBILE_TOKEN:'APP',
  WECHAT_MINIAPP_LOGIN_CODE:'MINIAPP', WECHAT_MINIAPP_PHONE_CODE:'MINIAPP', WECOM_MINIAPP_CODE:'MINIAPP',
  FEISHU_WEBVIEW_CODE:'WEBVIEW', DINGTALK_WEBVIEW_CODE:'WEBVIEW',
});
export function externalCodeAllowed(client, codeType, source) {
  const scene = codeScenes[codeType];
  if (!scene) return false;
  const type = client.applicationType;
  if (!type) return true; // 未分类历史配置仍显示原值，不能推断或静默改写架构。
  const capability = source?.capabilities?.find(item => item.codeType === codeType);
  if (capability?.available === false) return false;
  if (capability?.applicationTypes && !capability.applicationTypes.includes(type)) return false;
  if (type === 'NATIVE') return scene === 'APP';
  if (type === 'IN_APP') return scene === 'WEBVIEW';
  if (type === 'MINIAPP') return scene === 'MINIAPP' &&
    (client.miniappPlatform === 'WECHAT' ? ['WECHAT_MINIAPP_LOGIN_CODE','WECHAT_MINIAPP_PHONE_CODE'].includes(codeType)
      : client.miniappPlatform === 'WECOM' && codeType === 'WECOM_MINIAPP_CODE');
  return false;
}
export function browserSourceAllowed(client, source) {
  if (!source?.browser) return false;
  const type = client.applicationType;
  if (!type) return true;
  if (!['WEB_SERVER','SPA','IN_APP'].includes(type)) return false;
  return browserCapabilitiesForType(client,source).length > 0;
}
const environmentLabels = Object.freeze({DESKTOP_BROWSER:'桌面浏览器',MOBILE_BROWSER:'手机浏览器',
  WECHAT_WEBVIEW:'微信内网页',WECOM_WEBVIEW:'企业微信内网页',FEISHU_WEBVIEW:'飞书内网页',DINGTALK_WEBVIEW:'钉钉内网页'});
export function browserEnvironmentLabel(source) {
  return (source.browserEnvironments || []).map(value => environmentLabels[value] || value).join('、');
}

// 正式目录逐项列出网页登录能力；旧目录继续保留原来单一入口，不能猜测新增能力。
const webCapabilities = new Set(['WECHAT_WEBSITE','WECOM_BROWSER','FEISHU_WEB','DINGTALK_WEB','GITHUB_OAUTH']);
const inAppCapabilities = new Set(['WECHAT_OFFICIAL_ACCOUNT','WECOM_WEBVIEW']);
function browserCapabilitiesForType(client,source) {
  const type = client.applicationType;
  const explicit = (source.capabilities || []).filter(item => item.browser && item.loginCapability && item.available !== false);
  if (!type) return explicit;
  const allowed = type === 'IN_APP' ? inAppCapabilities : ['WEB_SERVER','SPA'].includes(type) ? webCapabilities : new Set();
  return explicit.filter(item => allowed.has(item.loginCapability) && (!item.applicationTypes || item.applicationTypes.includes(type)));
}
export function browserCapabilities(client,source) {
  if (!source?.browser) return [];
  const result = browserCapabilitiesForType(client,source);
  if (result.length || source.capabilities?.some(item => item.loginCapability)) return result;
  // 旧目录没有能力标识时只保留通用网页，不猜端内入口。
  return !client.applicationType || ['WEB_SERVER','SPA'].includes(client.applicationType) ? [{loginCapability:'BROWSER',label:'IAM网页登录'}] : [];
}
export const singleSourceApplication = client => ['NATIVE','MINIAPP','IN_APP'].includes(client.applicationType);
/** 数量按注册来源计算；微信小程序两个Code可共同指向同一注册。 */
export function sourceSelectionIssue(client, additions) {
  if (!singleSourceApplication(client)) return '';
  if (new Set(additions.map(item => String(item.source.id))).size > 1) return '当前应用只能选择一个身份源';
  if (['NATIVE','IN_APP'].includes(client.applicationType) && additions.length > 1) return '当前应用只能选择一个登录能力';
  return '';
}
