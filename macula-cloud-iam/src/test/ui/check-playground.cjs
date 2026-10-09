// Author: Rain. Runs against the isolated PlaygroundHttpIntegrationTest server, never production.
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.PLAYGROUND_TEST_URL;
if (!base || !/^http:\/\/127\.0\.0\.1:\d+$/.test(base)) throw new Error('Set PLAYGROUND_TEST_URL to the isolated loopback fixture');
const artifacts = path.resolve(__dirname, '../../../target/playground-browser');
fs.mkdirSync(artifacts, {recursive:true});
(async () => {
  const browser = await chromium.launch({channel:'chrome',headless:true});
  const results = [];
  try {
    for (const [width,height] of [[320,640],[390,844],[844,390],[1440,960]]) {
      const context = await browser.newContext({viewport:{width,height},permissions:['clipboard-read','clipboard-write']});
      context.setDefaultTimeout(15000); context.setDefaultNavigationTimeout(15000);
      const page = await context.newPage();
      const errors=[]; page.on('pageerror', error=>errors.push(error.message));
      await page.goto(base+'/playground');
      await page.locator('#status').filter({hasText:'环境就绪'}).waitFor();
      for (const scene of ['h5','mobile','device','server','legacy']) {
        await page.locator('[data-scene='+scene+']').click();
        assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,width+' '+scene+' overflow');
        for(const button of await page.locator('button:visible').all()) assert.ok((await button.boundingBox()).height>=44);
      }
      await page.locator('[data-scene=h5]').click();
      await page.locator('#copy-example').click();
      assert.match(await page.evaluate(()=>navigator.clipboard.readText()), /<ORIGINAL_VERIFIER>/);
      await page.locator('#start').focus();
      await page.keyboard.press('Tab');
      assert.equal(await page.locator('#reset').evaluate(node=>node===document.activeElement),true);
      await page.screenshot({path:path.join(artifacts,'playground-'+width+'.png'),fullPage:true});
      assert.deepEqual(errors,[]);
      results.push({case:'responsive-scenes-keyboard-copy',width,height,passed:true});
      console.log('Responsive checks passed: '+width);
      await context.close();
    }
    const client = await browser.newContext();
    client.setDefaultTimeout(15000); client.setDefaultNavigationTimeout(15000);
    const page = await client.newPage();
    await page.goto(base+'/playground');
    await page.locator('#status').filter({hasText:'环境就绪'}).waitFor();
    await page.locator('#start').click();
    await page.waitForURL('**/login');
    await page.locator('#username').fill('fixture-user');
    await page.locator('#password').fill('fixture-password');
    await page.locator('#password-login button[type=submit]').click();
    await page.waitForURL(url=>['/oauth2/consent','/playground'].includes(url.pathname));
    if(new URL(page.url()).pathname==='/oauth2/consent') {
      for(const checkbox of await page.locator('input[name=scope][type=checkbox]').all()) await checkbox.check();
      await page.locator('button[value=allow]').click();
    }
    await page.waitForURL(base+'/playground');
    await page.locator('#verification-state').filter({hasText:'已验签'}).waitFor();
    assert.equal(await page.locator('[data-action=refresh]').isVisible(),false);
    assert.equal(await page.evaluate(()=>sessionStorage.getItem('iam-playground-binding')),null);
    await page.locator('[data-action=userinfo]').click();
    await page.locator('#transcript').filter({hasText:'已验证 sub'}).waitFor();
    await page.locator('[data-action=reveal]').click();
    await page.locator('#tokens').filter({hasText:'access_token'}).waitFor();
    assert.equal(await page.evaluate(()=>localStorage.length),0);
    await page.locator('#hide-tokens').click();
    assert.equal(await page.locator('#revealed').isVisible(),false);
    results.push({case:'real-browser-pkce-oidc-userinfo-mask',passed:true});
    console.log('PKCE browser checks passed');

    await page.locator('[data-scene=device]').click();
    await page.locator('#start').click();
    await page.locator('#device-result').waitFor({state:'visible'});
    const userCode=await page.locator('#user-code').innerText();
    const verification=await page.locator('#verify-device').getAttribute('href');
    const user=await browser.newContext();
    user.setDefaultTimeout(15000); user.setDefaultNavigationTimeout(15000);
    const confirm=await user.newPage();
    confirm.on('response', response=> {
      if(new URL(response.url()).pathname==='/oauth2/device_verification' && response.request().method()==='POST')
        console.log('Device consent POST status: '+response.status());
    });
    await confirm.goto(verification);
    await confirm.waitForURL('**/login');
    await confirm.locator('#username').fill('fixture-user');
    await confirm.locator('#password').fill('fixture-password');
    await confirm.locator('#password-login button[type=submit]').click();
    await confirm.waitForURL('**/playground/device?**');
    await confirm.getByRole('button',{name:'核对并继续'}).click();
    await confirm.getByRole('button',{name:'允许连接'}).waitFor();
    assert.equal(await confirm.locator('.user-code').innerText(),userCode);
    await confirm.getByRole('button',{name:'允许连接'}).click();
    console.log('Device confirmation result: '+new URL(confirm.url()).searchParams.get('result'));
    await confirm.getByRole('heading',{name:'已批准设备连接'}).waitFor();
    await page.locator('#status').filter({hasText:'已获得演示令牌'}).waitFor({timeout:20000});
    assert.equal(await page.locator('#stop').isVisible(),false);
    assert.equal(await page.locator('[data-action=userinfo]').isVisible(),false);
    results.push({case:'real-two-context-device-approval-poll',passed:true});
    await user.close();

    await page.locator('#reset').click();
    await page.locator('#start').click();
    await page.locator('#device-result').waitFor({state:'visible'});
    await page.locator('#stop').click();
    assert.equal(await page.locator('#stop').isVisible(),false);
    results.push({case:'device-stop',passed:true});
    await page.locator('[data-scene=server]').click();
    await page.locator('#variant').selectOption('CLIENT_CREDENTIALS');
    let pendingRequests = 0;
    await page.route('**/api/v1/iam-playground/flows', () => { pendingRequests += 1; });
    await page.locator('#start').click();
    await page.waitForFunction(() => document.querySelector('#start-form').getAttribute('aria-busy') === 'true');
    assert.equal(await page.locator('#start').isDisabled(), true);
    await page.locator('#start-form').evaluate(form => {
      form.dispatchEvent(new Event('submit', {bubbles:true,cancelable:true}));
      form.dispatchEvent(new Event('submit', {bubbles:true,cancelable:true}));
    });
    await page.locator('#error').filter({hasText:'请求超时'}).waitFor({timeout:20000});
    assert.equal(pendingRequests, 1);
    assert.equal(await page.locator('#start').isEnabled(), true);
    assert.equal(await page.locator('#start-form').getAttribute('aria-busy'), 'false');
    await page.unroute('**/api/v1/iam-playground/flows');
    results.push({case:'mock-timeout-loading-duplicate-submit',passed:true});
    await page.route('**/api/v1/iam-playground/flows',route=>route.abort('failed'));
    await page.locator('#start').click();
    await page.locator('#error').waitFor({state:'visible'});
    assert.equal(await page.locator('#start').isEnabled(),true);
    await page.unroute('**/api/v1/iam-playground/flows');
    await page.locator('#start').click();
    await page.locator('#status').filter({hasText:'已获得演示令牌'}).waitFor();
    await page.locator('[data-action=introspect]').click();
    await page.locator('#transcript').filter({hasText:'"active": true'}).waitFor();
    await page.locator('[data-action=revoke-access]').click();
    await page.locator('#http-status').filter({hasText:'HTTP 200'}).waitFor();
    await page.locator('[data-action=introspect]').click();
    await page.locator('#transcript').filter({hasText:'"active": false'}).waitFor();
    results.push({case:'mock-network-recovery-then-real-credentials-revocation',passed:true});
    await client.close();
    console.log(JSON.stringify(results,null,2));
  } finally { await browser.close(); }
})().catch(error=>{console.error(error.message.split('\n')[0]);process.exitCode=1;});
