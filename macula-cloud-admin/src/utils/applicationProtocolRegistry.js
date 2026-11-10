// 单应用只保留一个协议；新协议须有独立配置实现后才加入目录。
export const APPLICATION_PROTOCOLS = Object.freeze([
  {code:'OIDC',label:'OIDC',description:'用户登录及身份资料',family:'oauth2'},
  {code:'OAUTH2_1',label:'OAuth 2.1',description:'授权访问 API',family:'oauth2'},
])
const csv = value => (value || '').split(',').map(item=>item.trim()).filter(Boolean)
export const enabledProtocolCodes = client => client.protocol ? [client.protocol] : client.enabledProtocols || []
export const protocolSupported = (client, code) => APPLICATION_PROTOCOLS.some(item=>item.code===code) &&
  !(code==='OIDC' && ['SERVICE'].includes(client.applicationType))
export function protocolDefaults(client, protocol) {
  const publicApp=['SPA','NATIVE','MINIAPP','IN_APP'].includes(client.applicationType)
  const service=client.applicationType==='SERVICE',mini=client.applicationType==='MINIAPP'
  return {kind:'oauth2',clientAuthenticationMethods:publicApp?'none':'client_secret_basic',
    authorizationGrantTypes:service?'client_credentials':mini?'urn:macula:params:oauth:grant-type:external_code':client.applicationType==='IN_APP'?'':'authorization_code',
    scopes:protocol==='OIDC'?'openid,profile':service?'':'profile',redirectUris:'',allowedOrigins:[],requireProofKey:!service,
    requireAuthorizationConsent:false,authorizationCodeTimeToLive:300,accessTokenTimeToLive:3600,tokenFormat:'reference',
    reuseRefreshTokens:false,refreshTokenTimeToLive:3600,idTokenSignatureAlgorithm:protocol==='OIDC'?'RS256':null,
    signingAlgorithm:null,jwkSetUrl:null}
}
export function selectProtocol(client, code) {
  if (!protocolSupported(client, code)) throw new Error('当前应用类型不支持该协议')
  client.protocol=code
  client.settings=protocolDefaults(client, code)
  client.externalCodeTypes=[]
  client.externalProviderBindings={}
  client.browserLoginProviderTypes=[]
  client.browserProviderBindings={}
  if (client.browserCapabilityBindings != null) client.browserCapabilityBindings={}
  client.directLoginProviderType=''
}
export function ensureProtocol(client, code=client.protocol) {
  if (!client.protocol) selectProtocol(client, code)
  if (client.protocol!==code) throw new Error('一个应用只能配置一个协议，请先切换协议')
  client.settings ||= protocolDefaults(client, code)
  return {protocol:code,enabled:true,settings:client.settings}
}
// 保留内部调用名称；启用其他协议必须替换当前配置，不生成第二份配置。
export function setProtocolEnabled(client, code, enabled) {
  if (!enabled) {client.configurationStatus='DRAFT';return false}
  const changed=client.protocol!==code
  if(changed)selectProtocol(client,code)
  return changed
}
export function projectProtocol(client, code=client.protocol) {
  if (!client.settings || client.protocol!==code) return {...client,settings:undefined,enabledProtocols:[],authorizationGrantTypes:'',clientAuthenticationMethods:'',scopes:'',redirectUris:'',allowedOrigins:[]}
  return {...client,...client.settings,settings:undefined,enabledProtocols:[code],defaultProtocol:code,
    externalCodeTypes:client.externalCodeTypes || [],externalProviderBindings:client.externalProviderBindings || {}}
}
export function anyProtocolUses(client, predicate) {
  return client.settings ? predicate(client.settings) : predicate(client)
}
export function managementProtocol(client) {
  return client.purpose==='RESOURCE_SERVER' ? 'RESOURCE_SERVER' : null
}
export function normalizeProtocol(client, code=client.protocol) {
  const settings=ensureProtocol(client,code).settings,old=JSON.stringify(settings)
  if (['SPA','NATIVE','MINIAPP'].includes(client.applicationType)) settings.clientAuthenticationMethods='none'
  let grants=csv(settings.authorizationGrantTypes)
  if (code==='OIDC') {
    grants=grants.filter(item=>['authorization_code','refresh_token','urn:macula:params:oauth:grant-type:external_code'].includes(item))
    // 默认授权在创建模板中设置；移除SDK来源后保持空授权，不自动变成网页登录。
    const scopes=csv(settings.scopes)
    if (!scopes.includes('openid')) scopes.unshift('openid')
    settings.scopes=scopes.join(',')
    settings.idTokenSignatureAlgorithm ||= 'RS256'
  } else settings.scopes=csv(settings.scopes).filter(item=>item!=='openid').join(',')
  if (settings.clientAuthenticationMethods==='none') {
    grants=grants.filter(item=>!['client_credentials','refresh_token'].includes(item))
    settings.requireProofKey=true
  } else grants=grants.filter(item=>!['sms','email','urn:macula:params:oauth:grant-type:external_code'].includes(item))
  if (client.applicationType==='SERVICE') grants=['client_credentials']
  if(grants.includes('client_credentials'))settings.scopes=csv(settings.scopes).filter(item=>item!=='profile').join(',')
  if (grants.includes('authorization_code')) settings.requireProofKey=true
  if (settings.clientAuthenticationMethods==='private_key_jwt') settings.signingAlgorithm ||= 'RS256'
  settings.authorizationGrantTypes=[...new Set(grants)].join(',')
  return old!==JSON.stringify(settings)
}
