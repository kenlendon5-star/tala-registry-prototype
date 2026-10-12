const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),read=p=>fs.readFileSync(path.join(root,p),'utf8');
const progress=JSON.parse(read('planning/02-progress/implementation-progress.json'));
const cards=read('planning/01-boards/ANDROID-30-DAY-PLAN.md').split(/(?=^### D\d\d )/m).filter(s=>/^### D(0[7-9]|1\d|20) /m.test(s)).map(s=>{
 const id=s.match(/^### (D\d\d)/)[1];return {id,title:s.split('\n')[0],claimed:progress[id],tasks:[...s.matchAll(/^- \[([x ])\] (.+)$/gm)].map(m=>({checked:m[1]==='x',task:m[2]})),acceptance:s.match(/\*\*Done when:\*\* ([^\n]+)/)?.[1],deliverable:s.match(/\*\*Evidence\/deliverable:\*\* ([^\n]+)/)?.[1],evidenceFile:'planning/03-evidence/'+id+'.md'};
});
const target=path.join(root,'docs/reviews/SPRINT-07-20-CRITERIA.json');fs.writeFileSync(target,JSON.stringify({auditedAt:'2026-10-12',scope:'Native Android work packages D07–D20; four actual weekly sprints exist in the plan.',cards},null,2)+'\n');
for(const c of cards)console.log(c.id+' '+c.title.split(' · ').slice(-1)[0]+'\n'+c.tasks.map((t,i)=>'  '+(i+1)+'. '+t.task).join('\n')+'\n  Acceptance: '+c.acceptance+'\n');
