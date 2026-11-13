import { describe, expect, it, vi } from "vitest";
import { generateKeyPairSync, sign, verify, randomUUID } from "node:crypto";
vi.mock("@/api/model/system/oauth2Client", () => ({
  AUTHORIZATION_CODE_GRANT: "authorization_code",
  EXTERNAL_CODE_GRANT: "urn:macula:params:oauth:grant-type:external_code",
}));
import {
  AUTHENTICATION_METHODS,
  applicationTemplate,
  clientPayload,
  AUTHORIZATION_GRANTS,
  basicAuthorization,
  configurationIssues,
  generateClientSecret,
  generateClientId,
  grantDependency,
  grantIssue,
  privateKeyExample,
  requestExample,
  shellQuote,
} from "@/utils/oauthClientConfiguration";
const externalGrant = "urn:macula:params:oauth:grant-type:external_code";
const externalChoice = { codeType: 'WECHAT_MINIAPP_LOGIN_CODE', source: { id: 7, available: true,
  capabilities: [{ codeType: 'WECHAT_MINIAPP_LOGIN_CODE' }] } };

const client = (
  auth = "none",
  grants = "authorization_code,refresh_token"
) => ({
  clientId: "test-client",
  enabledProtocols:["OIDC","OAUTH2_1"],defaultProtocol:"OIDC",configurationStatus:"ENABLED",
  requireProofKey: true,
  clientAuthenticationMethods: auth,
  authorizationGrantTypes: grants,
  scopes: "openid,profile",
  redirectUris: "https://app.example.com/callback",
  externalCodeTypes: ["WECHAT_MINIAPP_LOGIN_CODE"],
  externalProviderBindings: { WECHAT_MINIAPP_LOGIN_CODE: 7 },
});

describe("OAuth Client configuration", () => {
  it("generates a UUID client identifier independently of credentials", () => {
    expect(generateClientId({ randomUUID: () => "native-uuid" })).toBe(
      "native-uuid"
    );
    const id = generateClientId({
      getRandomValues(bytes) {
        bytes.fill(0xab);
        return bytes;
      },
    });
    expect(id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
    );
    expect(() => generateClientId({})).toThrow("生成 Client ID");
  });

  for (const auth of AUTHENTICATION_METHODS) {
    for (const grant of AUTHORIZATION_GRANTS) {
      it(`${auth.value} / ${grant.value} follows product policy`, () => {
        const forbidden =
          auth.value === "none"
            ? ["client_credentials", "refresh_token"].includes(grant.value)
            : ["sms", "email", externalGrant].includes(grant.value);
        expect(Boolean(grantIssue(client(auth.value), grant.value))).toBe(
          forbidden
        );
      });
    }
  }
  it("rejects confidential verification grants regardless of previous combinations", () => {
    const original = client(
      "client_secret_basic,client_secret_post",
      "sms,authorization_code,refresh_token"
    );
    original.externalCodeTypes = [];
    const reordered = {
      ...original,
      clientAuthenticationMethods: "client_secret_post,client_secret_basic",
      authorizationGrantTypes: "refresh_token,sms,authorization_code",
    };
    expect(configurationIssues(reordered, original).join(" ")).toContain("退役");
    expect(grantIssue(reordered, "email", original)).not.toBe("");
    expect(grantIssue(reordered, "sms", original)).not.toBe("");
    for (const changed of [
      { ...original, authorizationGrantTypes: "sms,email" },
      { ...original, authorizationGrantTypes: "sms" },
      { ...original, clientAuthenticationMethods: "client_secret_basic" },
    ]) {
      expect(configurationIssues(changed, original).length).toBeGreaterThan(0);
    }
  });
  it("reports unknown grants, invalid auth and selected dependencies", () => {
    expect(configurationIssues(client("none", "future_grant")).join(" ")).toContain(
      "future_grant"
    );
    expect(
      configurationIssues(client("none,client_secret_basic")).join(" ")
    ).toContain("认证方式");
    expect(configurationIssues(client("none", "")).join(" ")).toContain("至少");
    expect(
      grantDependency(
        { browserLoginProviderTypes: ["GITHUB"] },
        "authorization_code"
      )
    ).toContain("网页登录");
    expect(grantDependency(client(), externalGrant)).toContain("外部凭证");
    expect(
      grantIssue({ ...client(), externalCodeTypes: [] }, externalGrant)
    ).toContain("先");
  });
  it("encodes Basic components before joining, including Unicode and delimiters", () => {
    expect(atob(basicAuthorization("a:b 空", "p:+&'密钥").slice(6))).toBe(
      "a%3Ab+%E7%A9%BA:p%3A%2B%26%27%E5%AF%86%E9%92%A5"
    );
  });
  it("generates 256-bit secrets and fails closed without secure random", () => {
    const source = {
      getRandomValues: vi.fn((bytes) => {
        bytes.fill(255);
        return bytes;
      }),
    };
    expect(generateClientSecret(source)).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(source.getRandomValues.mock.calls[0][0]).toHaveLength(32);
    expect(() => generateClientSecret({})).toThrow("安全生成");
    expect(() =>
      generateClientSecret({
        getRandomValues() {
          throw new Error("denied");
        },
      })
    ).toThrow("denied");
  });
  it("quotes shell syntax and keeps credentials out of URLs", () => {
    expect(shellQuote("a'b\n$(`cmd`)")).toBe("'a'\"'\"'b\n$(`cmd`)'");
    const c = client("client_secret_post", "client_credentials");
    const text = requestExample(c, {
      grant: "client_credentials",
      secret: "a'b\n$(x)`x`",
      baseUrl: "https://iam.example.com",
    });
    expect(text).toContain(
      "--data-urlencode 'client_secret=a'\"'\"'b\n$(x)`x`'"
    );
    expect(text).toContain("服务身份");
    expect(text).not.toContain("?client_secret");
    expect(text).not.toContain("refresh_token=");
    expect(
      requestExample(c, {
        grant: "client_credentials",
        baseUrl: "https://iam.example.com?x=1",
      })
    ).toContain("请输入");
  });
  it("generates grant-specific requests with PKCE, typed codes and assertions", () => {
    const c = client(
      "none",
      AUTHORIZATION_GRANTS.map((g) => g.value).join(",")
    );
    const code = requestExample(c, { grant: "authorization_code" });
    expect(code).toContain("/oauth2/authorize?");
    expect(code).toContain("code_challenge_method=S256");
    expect(code).toContain("code_verifier=");
    expect(code).not.toContain("client_secret");
    for (const [grant, required] of [
      ["refresh_token", "refresh_token="],
      [externalGrant, "code_type=WECHAT_MINIAPP_LOGIN_CODE"],
    ])
      expect(requestExample(c, { grant, ...(grant === externalGrant ? externalChoice : {}) })).toContain(required);
    expect(
      requestExample(client("private_key_jwt"), { grant: "authorization_code" })
    ).toContain("client_assertion_type=");
    expect(
      requestExample(client("client_secret_basic"), {
        grant: "authorization_code",
        secret: "unit-only-secret",
      })
    ).toContain(basicAuthorization("test-client", "unit-only-secret"));
    expect(requestExample(c, { grant: "unknown" })).toContain("不受支持");
    expect(requestExample(c, { grant: "password" })).toContain("不受支持");
  });
  it.each(["RS256", "RS384", "RS512"])(
    "produces a valid short-lived %s client assertion",
    (algorithm) => {
      const { privateKey, publicKey } = generateKeyPairSync("rsa", {
        modulusLength: 2048,
      });
      const sample = privateKeyExample({ signingAlgorithm: algorithm });
      const runnable = sample.replace(/^import .*;$/gm, "");
      let token;
      const now = Math.floor(Date.now() / 1000);
      new Function(
        "process",
        "readFileSync",
        "sign",
        "randomUUID",
        "console",
        runnable
      )(
        {
          env: {
            CLIENT_ID: "client-test",
            JWT_AUDIENCE: "https://iam.example.com/oauth2/token",
            KEY_ID: "test-key",
            PRIVATE_KEY_FILE: "/test-key",
          },
        },
        () => privateKey,
        sign,
        randomUUID,
        {
          log(value) {
            token = value;
          },
        }
      );
      const [header, payload, signature] = token.split(".");
      const claims = JSON.parse(Buffer.from(payload, "base64url"));
      expect(JSON.parse(Buffer.from(header, "base64url"))).toMatchObject({
        alg: algorithm,
        kid: "test-key",
      });
      expect(claims).toMatchObject({
        iss: "client-test",
        sub: "client-test",
        aud: "https://iam.example.com/oauth2/token",
      });
      expect(claims.exp).toBeGreaterThanOrEqual(now + 60);
      expect(claims.exp).toBeLessThanOrEqual(now + 62);
      expect(claims.jti).toBeTruthy();
      expect(
        verify(
          `RSA-SHA${algorithm.slice(2)}`,
          Buffer.from(`${header}.${payload}`),
          publicKey,
          Buffer.from(signature, "base64url")
        )
      ).toBe(true);
    }
  );
});

describe("application architecture templates", () => {
  it("clears type-dependent settings while retaining the same generated identity", () => {
    const old = {
      clientId: "stable-id",
      clientName: "name",
      tenantId: "1",
      accessTokenTimeToLive: 777,
      redirectUris: "https://old.example",
      browserLoginProviderTypes: ["WECOM"],
      externalCodeTypes: ["WECOM_APP_CODE"],
      externalProviderBindings: { WECOM_APP_CODE: 7 },
      clientSecret: "old-secret",
    };
    const spa = applicationTemplate(old, "SPA");
    expect(spa).toMatchObject({
      clientId: "stable-id",
      clientName: "name",
      accessTokenTimeToLive: 777,
      clientAuthenticationMethods: "none",
      requireProofKey: true,
      redirectUris: "",
      browserLoginProviderTypes: [],
      externalCodeTypes: [],
      externalProviderBindings: {},
      clientSecret: "",
    });
    const service = applicationTemplate(spa, "SERVICE");
    expect(service.authorizationGrantTypes).toBe("client_credentials");
    expect(service.scopes).toBe("");
    expect(service.hostedLoginMethods).toEqual([]);
    const mini = applicationTemplate(service, "MINIAPP", "WECOM");
    expect(mini.miniappPlatform).toBe("WECOM");
    expect(mini.externalProviderBindings).toEqual({});
  });
  it("preserves policy null/empty/omitted and excludes temporary secrets", () => {
    const base = {
      id: 7,
      clientId: "existing",
      clientAuthenticationMethods: "client_secret_basic",
      secretConfigured: true,
      clientSecret: "temporary",
      exampleSecret: "temporary",
    };
    expect(clientPayload(base)).not.toHaveProperty("hostedLoginMethods");
    expect(
      clientPayload({ ...base, hostedLoginMethods: null }).hostedLoginMethods
    ).toBeNull();
    expect(
      clientPayload({ ...base, hostedLoginMethods: [] }).hostedLoginMethods
    ).toEqual([]);
    expect(clientPayload(base)).not.toHaveProperty("clientSecret");
    expect(clientPayload(base)).not.toHaveProperty("exampleSecret");
    expect(
      clientPayload({ ...base, secretConfigured: false }).clientSecret
    ).toBe("temporary");
  });
  it("uses actual Discovery endpoints and refuses missing endpoints", () => {
    const record = client("none", "authorization_code");
    expect(
      requestExample(record, {
        baseUrl: "https://iam.example",
        endpoints: {
          authorization_endpoint: "https://iam.example/auth",
          token_endpoint: "https://iam.example/token",
        },
        grant: "authorization_code",
      })
    ).toContain("https://iam.example/token");
    expect(
      requestExample(record, {
        baseUrl: "https://iam.example",
        endpoints: {},
        grant: "authorization_code",
      })
    ).toContain("Discovery未发布");
  });
});

describe('OIDC SDK examples',()=>{
 it('keeps openid and optional nonce only for the identity protocol',()=>{
  const base={...client('none',externalGrant),protocol:'OIDC',defaultProtocol:'OIDC',enabledProtocols:['OIDC'],applicationType:'MINIAPP',miniappPlatform:'WECHAT',redirectUris:''};
  expect(configurationIssues(base)).toEqual([]);
  const oidc=requestExample(base,{grant:externalGrant,...externalChoice});expect(oidc).toContain('scope=openid profile');expect(oidc).toContain('nonce=<OPTIONAL_RANDOM_NONCE>');expect(oidc).not.toContain('code_verifier=');
  const oauth=requestExample({...base,protocol:'OAUTH2_1',defaultProtocol:'OAUTH2_1',enabledProtocols:['OAUTH2_1'],scopes:'profile'},{grant:externalGrant,...externalChoice});expect(oauth).not.toContain('openid');expect(oauth).not.toContain('nonce=');
 });
});

describe('explicit provider capability examples', () => {
  it('requires one explicitly selected linked and available source', () => {
    const configured = client('none', externalGrant);
    expect(requestExample(configured, { grant: externalGrant })).not.toContain('curl');
    expect(requestExample(configured, { grant: externalGrant, ...externalChoice,
      source: { ...externalChoice.source, available: false } })).not.toContain('curl');
    expect(requestExample(configured, { grant: externalGrant, ...externalChoice,
      source: { ...externalChoice.source, id: 999 } })).not.toContain('curl');
    expect(requestExample({ ...configured, externalProviderBindings: {} },
      { grant: externalGrant, ...externalChoice })).not.toContain('curl');
  });
  it('uses the selected second type and never defaults to the first', () => {
    const configured = client('none', externalGrant);
    configured.externalCodeTypes.push('DINGTALK_WEBVIEW_CODE');
    configured.externalProviderBindings.DINGTALK_WEBVIEW_CODE = 9;
    const example = requestExample(configured, { grant: externalGrant, codeType: 'DINGTALK_WEBVIEW_CODE',
      source: { id: 9, available: true, capabilities: [{ codeType: 'DINGTALK_WEBVIEW_CODE' }] } });
    expect(example).toContain('code_type=DINGTALK_WEBVIEW_CODE');
    expect(example).not.toContain('WECHAT_MINIAPP_LOGIN_CODE');
    expect(example).not.toContain('client_secret');
  });
});

describe('小程序双登录入口',()=>{
 it('允许仅IAM统一登录和两种入口并存，仍拒绝错误平台Code',()=>{
  const hosted={...client('none','authorization_code'),applicationType:'MINIAPP',miniappPlatform:'WECHAT',externalCodeTypes:[],externalProviderBindings:{}};
  expect(configurationIssues(hosted)).toEqual([]);
  const both={...hosted,authorizationGrantTypes:'authorization_code,'+externalGrant,externalCodeTypes:['WECHAT_MINIAPP_LOGIN_CODE'],externalProviderBindings:{WECHAT_MINIAPP_LOGIN_CODE:7}};
  expect(configurationIssues(both)).toEqual([]);
  expect(configurationIssues({...both,externalCodeTypes:['WECOM_MINIAPP_CODE']}).length).toBeGreaterThan(0);
 });
});


it('omits absent legacy browser capability bindings but preserves an explicit multi-registration map', async () => {
  const {clientPayload}=await import('@/utils/oauthClientConfiguration');
  expect(clientPayload({browserCapabilityBindings:null})).not.toHaveProperty('browserCapabilityBindings');
  const map={WECHAT_WEBSITE:7,WECHAT_OFFICIAL_ACCOUNT:8};
  expect(clientPayload({browserCapabilityBindings:map}).browserCapabilityBindings).toEqual(map);
});


describe('AppLink runtime callback', () => {
  it('requires an HTTPS Client callback for pure external code and includes it in the request example', () => {
    const app={...client('none',externalGrant),applicationType:'NATIVE',externalCodeTypes:['FEISHU_APP_CODE'],
      externalProviderBindings:{FEISHU_APP_CODE:7},redirectUris:'http://127.0.0.1/callback,https://app.example/callback'};
    expect(configurationIssues(app).join(' ')).not.toContain('HTTPS授权回调');
    const source={id:7,providerType:'FEISHU',sourceType:'FEISHU_WEB',available:true,
      capabilities:[{codeType:'FEISHU_APP_CODE',loginCapability:'FEISHU_APPLINK',providerPkce:true,flow:'FEISHU_APPLINK_V3'}]};
    const example=requestExample(app,{grant:externalGrant,codeType:'FEISHU_APP_CODE',source});
    expect(example).toContain('provider_code_verifier');
    expect(example).toContain('provider_redirect_uri=https://app.example/callback');
    app.redirectUris='http://127.0.0.1/callback';
    expect(configurationIssues(app).join(' ')).toContain('HTTPS授权回调');
    expect(requestExample(app,{grant:externalGrant,codeType:'FEISHU_APP_CODE',source})).toContain('登记AppLink');
  });
});

// EMAIL是托管认证方式；显式取消不能在序列化时被默认值补回。
it("keeps email defaults separate from explicit disabled choices", () => {
  expect(applicationTemplate({}, "SPA").hostedLoginMethods).toEqual(["PASSWORD", "SMS", "EMAIL"]);
  expect(clientPayload({...client(), hostedLoginMethods:["PASSWORD", "EMAIL"]}).hostedLoginMethods).toEqual(["PASSWORD", "EMAIL"]);
  expect(clientPayload({...client(), hostedLoginMethods:["PASSWORD"]}).hostedLoginMethods).toEqual(["PASSWORD"]);
  expect(clientPayload({...client(), hostedLoginMethods:[]}).hostedLoginMethods).toEqual([]);
});

it.each(['WEB_SERVER','SPA'])('新建%s默认浏览器，模式字段与协议配置独立', applicationType=>{
 const draft=applicationTemplate({},applicationType);
 expect(draft.webLoginMode).toBe('BROWSER');
 const created={...draft,webLoginMode:'IN_APP',protocol:null,settings:null,hostedLoginMethods:[]};
 const payload=clientPayload(created);
 expect(payload.webLoginMode).toBe('IN_APP');expect(payload.settings).toBeNull();expect(payload.protocol).toBeNull();
 expect(payload.hostedLoginMethods).toEqual([]);
 expect(clientPayload(applicationTemplate({},'NATIVE'))).not.toHaveProperty('webLoginMode');
});
