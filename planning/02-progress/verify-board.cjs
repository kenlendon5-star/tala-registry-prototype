const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try{
  const page=await browser.newPage({viewport:{width:1600,height:1000}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(pathToFileURL(path.join(__dirname,'../01-boards/ANDROID-KANBAN.html')).href);
  assert.equal(await page.locator('.card').count(),30);
  // Repository baseline: D01–D05 are Done; nothing is Ready, verifying or building.
  const initialDoneCount=await page.locator('.column[data-status="Done"] .card').count();
  assert.equal(initialDoneCount,5);
  assert.equal(await page.locator('.column[data-status="Ready"] .card').count(),0);
  assert.equal(await page.locator('.column[data-status="Verify"] .card').count(),0);
  // Free the single WIP slot in this isolated test profile before exercising cards.
  for(const status of await page.locator('.column[data-status="In progress"] select[aria-label^="Status for"]').all()) {
   await status.selectOption('Backlog');
  }
  assert.match(await page.locator('#endDate').textContent(),/28 Oct 2026/);
  // Dependency gate: D07 waits for D06, which is not Done yet.
  await page.getByLabel('Status for D07').selectOption('Ready');
  assert.equal(await page.getByLabel('Status for D07').inputValue(),'Backlog');
  // The refusal reason is shown on the card, not only in the notice bar at the top.
  assert.match(await page.locator('[data-id="D07"] .gate').textContent(),/waiting for D06/);
  // Done gate: every checklist item and evidence note are required first.
  await page.getByLabel('Status for D06').selectOption('Done');
  assert.equal(await page.getByLabel('Status for D06').inputValue(),'Backlog');
  assert.match(await page.locator('[data-id="D06"] .gate').textContent(),/Check every task/);
  // WIP gate: one card building at a time.
  await page.getByLabel('Status for D01').selectOption('In progress');
  assert.equal(await page.getByLabel('Status for D01').inputValue(),'In progress');
  await page.getByLabel('Status for D06').selectOption('In progress');
  assert.equal(await page.getByLabel('Status for D06').inputValue(),'Backlog');
  assert.match(await page.locator('[data-id="D06"] .gate').textContent(),/WIP limit reached/);
  // Return D01 to Done (its checklist and evidence are already recorded) to free the slot.
  await page.getByLabel('Status for D01').selectOption('Done');
  assert.equal(await page.getByLabel('Status for D01').inputValue(),'Done');
  // Completing D06's checklist and evidence lets it reach Done and clears the refusal reason.
  await page.locator('[data-id="D06"] summary').click();
  for(const check of await page.locator('[data-id="D06"] input[type=checkbox]').all())await check.check();
  await page.getByLabel('Evidence or blocker notes for D06').fill('Board verification fixture: editing and saves reviewed against the Day 5 storage evidence.');
  await page.getByLabel('Status for D06').selectOption('Done');
  assert.equal(await page.locator('.column[data-status="Done"] .card').count(),initialDoneCount+1);
  assert.equal(await page.locator('[data-id="D06"] .gate').count(),0);
  await page.reload();
  assert.equal(await page.getByLabel('Status for D01').inputValue(),'Done');
  assert.equal(await page.getByLabel('Status for D06').inputValue(),'Done');
  assert.equal(await page.getByLabel('Status for D07').inputValue(),'Backlog');
  const download=page.waitForEvent('download');await page.getByRole('button',{name:'Export progress'}).click();
  assert.equal((await download).suggestedFilename(),'tala-native-android-progress.json');
  const raw=await page.evaluate(()=>JSON.stringify(state));
  await page.getByLabel('Show sprint').selectOption('4');
  assert.equal(await page.locator('.card').count(),9);
  await page.getByLabel('Start date').fill('2026-10-01');await page.getByLabel('Start date').press('Tab');
  assert.match(await page.locator('#endDate').textContent(),/30 Oct 2026/);
  await page.locator('#fileInput').setInputFiles({name:'bad.json',mimeType:'application/json',buffer:Buffer.from('{}')});
  await page.waitForFunction(()=>document.querySelector('#notice').textContent.startsWith('Import failed:'));
  assert.equal(await page.getByLabel('Start date').inputValue(),'2026-10-01');
  page.once('dialog',d=>d.accept());
  await page.locator('#fileInput').setInputFiles({name:'restore.json',mimeType:'application/json',buffer:Buffer.from(raw)});
  await page.waitForFunction(()=>document.querySelector('#notice').textContent.startsWith('Progress restored'));
  assert.equal(await page.getByLabel('Start date').inputValue(),'2026-09-29');
  assert.equal(await page.getByLabel('Status for D02').inputValue(),'Done');
  // Capture repository defaults, keeping this test's temporary progress out of the deliverable.
  await page.evaluate(()=>localStorage.clear());await page.reload();
  await page.getByLabel('Show sprint').selectOption('1');
  await page.locator('[data-id="D01"] summary').click();
  await page.screenshot({path:path.join(__dirname,'../01-boards/board-desktop.png')});
  await page.setViewportSize({width:390,height:844});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await page.screenshot({path:path.join(__dirname,'../01-boards/board-mobile.png')});
  await page.setViewportSize({width:320,height:800});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  assert.deepEqual(errors,[]);
  console.log('Board passed: 30 cards, baseline D01–D05 Done, dependency/Done/WIP gates with inline reasons, progress persistence, export/import, date shifting, sprint filter, mobile widths and no script errors.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
