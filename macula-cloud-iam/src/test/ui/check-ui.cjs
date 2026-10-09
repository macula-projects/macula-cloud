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
    for (const view of ['login','consent']) {
      await page.goto(baseUrl+'/'+view+'.html');
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true,name+' '+view+' overflow');
      assert.equal(await page.locator('h1').last().isVisible(),true);
      for (const button of await page.locator('button:visible').all()) assert.ok((await button.boundingBox()).height>=44);
      await page.screenshot({path:artifacts+'/'+view+'-'+name+'.png',fullPage:true});
      if(view==='login'){
        await page.locator('#username').focus();
        await page.keyboard.press('Tab');
        assert.equal(await page.locator('#password').evaluate(input=>input===document.activeElement),true);
        await page.getByLabel('账号',{exact:true}).fill('fixture-user');
        await page.getByLabel('密码',{exact:true}).fill('fixture-password');
        await page.getByRole('button',{name:'显示或隐藏密码'}).click();
        assert.equal(await page.locator('#password').getAttribute('type'),'text');
        await page.route('**/login',route=>route.fulfill({status:401,contentType:'application/json',body:'{"success":false}'}));
        await page.getByRole('button',{name:'登录并继续'}).click();
        await page.locator('[role=alert]').filter({hasText:'登录失败'}).waitFor();
        assert.equal(await page.getByRole('button',{name:'登录并继续'}).isEnabled(),true);
        await page.unroute('**/login');
        await page.route('**/login',route=>route.abort('failed'));
        await page.getByRole('button',{name:'登录并继续'}).click();
        await page.locator('[role=alert]').filter({hasText:'网络连接失败'}).waitFor();
        await page.unroute('**/login');
        // Accelerate only the application's 20-second deadline; exercise its real AbortController path.
        await page.evaluate(()=>{
          const original=window.setTimeout;
          window.setTimeout=(fn,ms,...args)=>original(fn,ms===20000?50:ms,...args);
        });
        await page.route('**/login',()=>{});
        await page.getByRole('button',{name:'登录并继续'}).click();
        await page.locator('[role=alert]').filter({hasText:'请求超时'}).waitFor();
        await page.unroute('**/login');
        await page.reload();
        await page.getByLabel('账号',{exact:true}).fill('fixture-user');
        await page.getByLabel('密码',{exact:true}).fill('fixture-password');
        let calls=0, release;
        const received=new Promise(resolve=>{
          page.route('**/login',route=>{calls++;release=()=>route.fulfill({status:200,contentType:'application/json',
            body:JSON.stringify({success:true,data:{targetUrl:'/consent.html'}})});resolve();});
        });
        await page.getByRole('button',{name:'登录并继续'}).click();
        await received;
        assert.equal(await page.locator('#password-login button[type=submit]').isDisabled(),true);
        await page.locator('#password-login').evaluate(form=>{
          form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
        });
        assert.equal(calls,1);
        await release();
        await page.waitForURL('**/consent.html');
        await page.unroute('**/login');
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
