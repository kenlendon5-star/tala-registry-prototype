const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const baseline=require('./implementation-progress.json');
const nextCard=Object.keys(baseline).find(id=>baseline[id].status==='Backlog');
const {pathToFileURL}=require('node:url');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try{
  const page=await browser.newPage({viewport:{width:1600,height:1000}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(pathToFileURL(path.join(__dirname,'../01-boards/ANDROID-KANBAN.html')).href);
  assert.equal(await page.locator('.card').count(),30);
  // Check every rendered status against the repository evidence baseline.
  const initialDoneCount=await page.locator('.column[data-status="Done"] .card').count();
  assert.equal(initialDoneCount,Object.values(baseline).filter(card=>card.status==='Done').length);
  assert.equal(await page.locator('.column[data-status="Ready"] .card').count(),0);
  assert.equal(await page.locator('.column[data-status="In progress"] .card').count(),Object.values(baseline).filter(card=>card.status==='In progress').length);
  for(const [id,card] of Object.entries(baseline))assert.equal(await page.getByLabel(`Status for ${id}`).inputValue(),card.status);
  assert.equal(await page.getByLabel('Status for D07').inputValue(),'Done');
  // Complete the in-progress or verifying card (if any) in this isolated profile so its dependents can
  // exercise their dependency gates. Baseline files stay untouched; localStorage is cleared below.
  let earlyCompleted=0;
  while(await page.locator('.column[data-status="In progress"] select[aria-label^="Status for"], .column[data-status="Verify"] select[aria-label^="Status for"]').count()){
   const status=page.locator('.column[data-status="In progress"] select[aria-label^="Status for"], .column[data-status="Verify"] select[aria-label^="Status for"]').first();
   const id=(await status.getAttribute('aria-label')).replace('Status for ','');
   const details=page.locator(`[data-id="${id}"] details`);
   if(!await details.evaluate(el=>el.open))await page.locator(`[data-id="${id}"] summary`).click();
   for(const check of await page.locator(`[data-id="${id}"] input[type=checkbox]`).all())await check.check();
   await page.getByLabel(`Evidence or blocker notes for ${id}`).fill('Board verification fixture: in-progress card completed so dependent gates can be exercised.');
   await status.selectOption('Done');
   earlyCompleted++;
  }
  assert.match(await page.locator('#endDate').textContent(),/28 Oct 2026/);
  // Dependency gate: D22 waits for D21, which is in Backlog rather than Done.
  await page.getByLabel('Status for D22').selectOption('Ready');
  assert.equal(await page.getByLabel('Status for D22').inputValue(),'Backlog');
  // The refusal reason is shown on the card, not only in the notice bar at the top.
  assert.match(await page.locator('[data-id="D22"] .gate').textContent(),/waiting for D21/);
  // Done gate: every checklist item and evidence note are required first; D08 still has unchecked tasks.
  await page.getByLabel(`Status for ${nextCard}`).selectOption('Done');
  assert.equal(await page.getByLabel(`Status for ${nextCard}`).inputValue(),'Backlog');
  assert.match(await page.locator(`[data-id="${nextCard}"] .gate`).textContent(),/(?:Check|Tick) every task/);
  // WIP gate: one card building at a time. D03's dependency (D02) stays Done while D01 builds.
  await page.getByLabel('Status for D01').selectOption('In progress');
  assert.equal(await page.getByLabel('Status for D01').inputValue(),'In progress');
  await page.getByLabel('Status for D03').selectOption('In progress');
  assert.equal(await page.getByLabel('Status for D03').inputValue(),'Done');
  assert.match(await page.locator('[data-id="D03"] .gate').textContent(),/WIP limit reached/);
  // Return D01 to Done (its checklist and evidence are already recorded) to free the slot.
  await page.getByLabel('Status for D01').selectOption('Done');
  assert.equal(await page.getByLabel('Status for D01').inputValue(),'Done');
  // Completing D08's checklist and evidence lets it reach Done (its dependency D07 is Done) and clears the refusal reason.
  const nextDetails=page.locator(`[data-id="${nextCard}"] details`);
  // A refused Done transition can already expand the checklist for the user.
  if(!await nextDetails.evaluate(el=>el.open))await page.locator(`[data-id="${nextCard}"] summary`).click();
  for(const check of await page.locator(`[data-id="${nextCard}"] input[type=checkbox]`).all())await check.check();
  await page.getByLabel(`Evidence or blocker notes for ${nextCard}`).fill('Board verification fixture: gates exercised in an isolated profile.');
  await page.getByLabel(`Status for ${nextCard}`).selectOption('Done');
  assert.equal(await page.locator('.column[data-status="Done"] .card').count(),initialDoneCount+1+earlyCompleted);
  assert.equal(await page.locator(`[data-id="${nextCard}"] .gate`).count(),0);
  await page.reload();
  assert.equal(await page.getByLabel('Status for D01').inputValue(),'Done');
  assert.equal(await page.getByLabel('Status for D07').inputValue(),'Done');
  assert.equal(await page.getByLabel(`Status for ${nextCard}`).inputValue(),'Done');
  assert.equal(await page.getByLabel('Status for D22').inputValue(),'Backlog');
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
  console.log(`Board passed: 30 cards matching the repository baseline (${initialDoneCount} Done), dependency/Done/WIP gates, progress persistence, export/import, date shifting, sprint filter, mobile widths and no script errors.`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
