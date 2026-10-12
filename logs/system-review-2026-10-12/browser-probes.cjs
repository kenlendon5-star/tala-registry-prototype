const { chromium } = require('playwright');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const results = {};
(async () => {
  const browser = await chromium.launch({headless:true,channel:'msedge'});
  try {
    const context = await browser.newContext();
    const a = await context.newPage();
    await a.goto('http://localhost:5173');
    await a.locator('#app button').first().waitFor();
    await a.evaluate(() => save());
    const b = await context.newPage();
    await b.goto('http://localhost:5173');
    await b.locator('#app button').first().waitFor();
    results.staleTabBefore = await b.evaluate(() => data.households[0].head);
    await a.evaluate(() => { data.households[0].head = 'NEWER AUDIT NAME'; save(); });
    results.staleTab = await b.evaluate(() => {
      const inMemory = data.households[0].head;
      const before = JSON.parse(localStorage.getItem('tala-demo-v1')).households[0].head;
      go('reports');
      return {inMemory,before,after:JSON.parse(localStorage.getItem('tala-demo-v1')).households[0].head};
    });
    results.validation = await a.evaluate(() => ({
      impossibleDateError: errorFor({key:'dob',type:'date'},{dob:'2025-02-31'}),
      invalidCallbackError: errorFor({key:'callbackDate',type:'datetime-local'},{callbackDate:'not-a-date'}),
      inventedOptionError: errorFor({key:'x',type:'multi',options:['Bank','None']},{x:['Invented']}),
      malformedPhoneResult: formatPhone('abc09171234567')
    }));
    results.failedSave = await a.evaluate(() => {
      go('profile');
      document.querySelector('#profile-name').value = 'UNSAVED AUDIT PROFILE';
      const original = Storage.prototype.setItem;
      Storage.prototype.setItem = () => { throw new DOMException('Audit quota','QuotaExceededError'); };
      try {
        document.querySelector('#profile-form').dispatchEvent(new Event('submit',{cancelable:true}));
        return { toast: document.querySelector('#toast').textContent, memory:data.profile.name,stored:JSON.parse(localStorage.getItem('tala-demo-v1')).profile.name };
      }
      finally { Storage.prototype.setItem = original; }
    });
    const corrupt = await browser.newContext();
    await corrupt.addInitScript(() => localStorage.setItem('tala-demo-v1','{}'));
    const c = await corrupt.newPage();
    const errors=[];
    c.on('pageerror', e=>errors.push(e.message));
    await c.goto('http://localhost:5173');
    results.corruptStorage = {errors, appText:await c.locator('#app').textContent()};
    let handler;
    const fakeHttp={createServer: fn=>{handler=fn;return {listen:()=>{}}}};
    vm.runInNewContext(fs.readFileSync('browser/server.cjs','utf8'),{
      require:name=>name==='http'?fakeHttp:require(name),__dirname:path.resolve('browser'),console
    });
    try { handler({url:'/%'},{});results.malformedUrl='no throw'; }
    catch(error) {results.malformedUrl=error.name+': '+error.message;}
    fs.writeFileSync(path.join(__dirname,'browser-probes.json'),JSON.stringify(results,null,2));
    console.log(JSON.stringify(results,null,2));
  } finally {
    await Promise.race([browser.close(),new Promise(r=>setTimeout(r,3000))]);
    setTimeout(()=>process.exit(process.exitCode||0),500).unref();
  }
})().catch(error=>{console.error(error);process.exit(1)});
