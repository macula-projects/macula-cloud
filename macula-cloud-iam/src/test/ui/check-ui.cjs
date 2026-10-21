const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const artifacts = require('node:path').resolve(__dirname, '../../../target/iam-preview');
const baseUrl = process.env.IAM_PREVIEW_URL || 'http://127.0.0.1:18743';
(async () => {
  const browser = await chromium.launch({channel:'chrome',headless:true});
  const results = [];
  for (const [name, width, height] of [['desktop',1440,960],['phone',390,844],['small',320,640],['landscape',844,390]]) {
    const page = await browser.newPage({viewport:{width,height},isMobile:width<700,hasTouch:width<700});
    const errors=[]; page.on('pageerror',e=>errors.push(e.message));
    for (const view of ['login','binding','consent']) {
      await page.goto(baseUrl+'/'+view+'.html');
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true,name+' '+view+' overflow');
      assert.equal(await page.locator('h1').last().isVisible(),true);
      for (const button of await page.locator('button:visible').all()) assert.ok((await button.boundingBox()).height>=44);
      await page.screenshot({path:artifacts+'/'+view+'-'+name+'.png',fullPage:true});
      if(view==='login'){
        assert.deepEqual(await page.locator('.external-button').evaluateAll(links =>
          links.map(link => link.getAttribute('href'))), [
          '/login/github',
          '/login/wecom',
          '/login/wecom/browser',
          '/login/wecom/webview'
        ]);
        for (const link of await page.locator('.external-button').all()) assert.ok((await link.boundingBox()).height>=44);
        await page.getByRole('tab',{name:'手机验证码'}).click();
        await page.getByLabel('手机号码',{exact:true}).fill('13800000000');
        const sendCodeUrl=await page.locator('[data-send-code]').getAttribute('data-send-code-url');
        assert.equal(sendCodeUrl,'api/v1/sms-code');
        assert.equal(new URL(sendCodeUrl,'https://example.test/iam/login').pathname,'/iam/api/v1/sms-code');
        await page.route('**/api/v1/sms-code',route=>route.fulfill({status:200,contentType:'application/json',body:'{"status":"submitted"}'}));
        await page.getByRole('button',{name:'获取验证码'}).click();
        assert.equal(await page.locator('[data-send-code]').isDisabled(),true);
        await page.locator('[data-send-code]').filter({hasText:'60 秒后重试'}).waitFor();
        assert.match(await page.locator('[data-send-code]').textContent(),/60 秒后重试/);
        await page.unroute('**/api/v1/sms-code');
        await page.getByRole('tab',{name:'账号密码'}).click();
        await page.locator('#username').focus();
        await page.keyboard.press('Tab');
        assert.equal(await page.locator('#password').evaluate(input=>input===document.activeElement),true);
        await page.getByLabel('账号',{exact:true}).fill('fixture-user');
        await page.getByLabel('密码',{exact:true}).fill('fixture-password');
        await page.getByRole('button',{name:'显示或隐藏密码'}).click();
        assert.equal(await page.locator('#password').getAttribute('type'),'text');
        let submitted;
        await page.route('**/login', route => {
          submitted = route.request();
          return route.fulfill({status:302,headers:{location:'/consent.html'},body:''});
        });
        await page.getByRole('button',{name:'登录并继续'}).click();
        await page.waitForURL('**/consent.html');
        assert.equal(submitted.method(),'POST');
        const loginData = new URLSearchParams(submitted.postData());
        assert.equal(loginData.get('username'),'fixture-user');
        assert.equal(loginData.get('password'),'fixture-password');
        assert.ok(loginData.get('_csrf'));
        await page.unroute('**/login');
      } else if(view==='binding') {
        const cancel = page.locator('form[action="/login/bind/cancel"]');
        assert.equal(await cancel.getAttribute('method'),'post');
        assert.ok(await cancel.locator('input[name="_csrf"]').getAttribute('value'));
        assert.ok((await cancel.getByRole('button',{name:'取消并返回登录'}).boundingBox()).height>=44);
      } else {
        const check = page.locator('input[type=checkbox][name=scope]');
        if(await check.count()) await check.first().check();
        let submitted;
        await page.route('**/oauth2/authorize',route=>{submitted=route.request().postData();return route.fulfill({status:200,contentType:'text/plain',body:'denied'});});
        await page.getByRole('button',{name:'拒绝授权'}).click();
        await page.waitForURL('**/oauth2/authorize');
        assert.equal(new URLSearchParams(submitted).has('scope'),false);
        await page.goto(baseUrl+'/consent.html');
        const allowed=page.locator('input[type=checkbox][name=scope]');
        if(await allowed.count()) {
          await allowed.first().focus();
          const before=await allowed.first().isChecked();
          await page.keyboard.press('Space');
          assert.equal(await allowed.first().isChecked(),!before);
          await allowed.first().check();
        }
        await page.locator('button[value=allow]').click();
        await page.waitForURL('**/oauth2/authorize');
        assert.equal(new URLSearchParams(submitted).has('scope'),true);
      }
      assert.deepEqual(errors,[]);
      results.push({view,width,height,overflow:false,interaction:'passed'});
    }
    await page.close();
  }
  console.log(JSON.stringify(results,null,2));
  await browser.close();
})().catch(error=>{console.error(error);process.exit(1)});
