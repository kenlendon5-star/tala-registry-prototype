const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
// Temporary diagnostic: which status moves does the board reject, and what does it say?
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try{
  const page=await browser.newPage();
  await page.goto(pathToFileURL(path.join(__dirname,'../01-boards/ANDROID-KANBAN.html')).href);
  const move=async(id,status)=>{
   await page.getByLabel('Status for '+id).selectOption(status);
   await page.waitForTimeout(120);
   const shown=await page.getByLabel('Status for '+id).inputValue();
   const notice=await page.locator('#notice').textContent();
   const column=await page.locator('.card[data-id="'+id+'"]').evaluate(el=>el.closest('.column').dataset.status);
   console.log(id.padEnd(4),'->',status.padEnd(12),'select='+shown.padEnd(12),'column='+column.padEnd(12)+' notice='+notice);
  };
  console.log('--- baseline columns ---');
  for(const s of ['Backlog','Ready','In progress','Verify','Blocked','Done']){
   const ids=await page.locator('.column[data-status="'+s+'"] .card').evaluateAll(ns=>ns.map(n=>n.dataset.id));
   console.log(s.padEnd(12),ids.join(', ')||'(empty)');
  }
  console.log('--- attempted moves ---');
  await move('D01','In progress');
  await move('D01','Done');
  await move('D02','In progress');
  await move('D03','Done');
  await move('D06','In progress');
  await move('D06','Ready');
  await move('D07','In progress');
  console.log('--- final columns ---');
  for(const s of ['Backlog','Ready','In progress','Verify','Blocked','Done']){
   const ids=await page.locator('.column[data-status="'+s+'"] .card').evaluateAll(ns=>ns.map(n=>n.dataset.id));
   console.log(s.padEnd(12),ids.join(', ')||'(empty)');
  }
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
