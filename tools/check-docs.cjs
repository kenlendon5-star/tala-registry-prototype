const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');const errors=[];
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(path.join(dir,e.name)):[path.join(dir,e.name)])}
const md=[...files(path.join(root,'planning/docs')),...files(path.join(root,'planning')),...['README.md','planning/prototype as web app/README.md','android-native/README.md','supabase/README.md','tools/README.md','logs/README.md'].map(p=>path.join(root,p))].filter(p=>p.endsWith('.md'));
let links=0;
for(const file of md){const source=fs.readFileSync(file,'utf8').replace(/```[\s\S]*?```/g,'');for(const match of source.matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)){let href=match[1].replace(/^<|>$/g,'').split(/\s+"/)[0];if(/^(?:https?:|mailto:|#|app:|codex:)/i.test(href))continue;href=decodeURIComponent(href.split('#')[0]);if(!href)continue;links++;if(!fs.existsSync(path.resolve(path.dirname(file),href)))errors.push(path.relative(root,file)+' -> '+href)}}
for(const file of files(path.join(root,'planning/docs/schemas')).filter(p=>p.endsWith('.json')).concat(path.join(root,'planning/02-progress/implementation-progress.json')))try{JSON.parse(fs.readFileSync(file,'utf8'))}catch(e){errors.push(path.relative(root,file)+': '+e.message)}
const spec=fs.readdirSync(path.join(root,'planning/docs/specifications')).filter(f=>/^0[1-6]-.+\.md$/.test(f));assert.equal(spec.length,6,'Six specifications required');
const inventory=JSON.parse(fs.readFileSync(path.join(root,'planning/docs/schemas/questionnaire-current.json'),'utf8'));assert.equal(inventory.sections.length,18);
assert.equal(inventory.sections.reduce((n,s)=>n+s.fields.length,0),94);
const html=fs.readFileSync(path.join(root,'planning/docs/design/wireframes.html'),'utf8');const ids=new Set([...html.matchAll(/id="(s\d+)"/g)].map(m=>m[1]));assert.equal(ids.size,12);
for(const [,id] of html.matchAll(/href="#(s\d+)"/g))if(!ids.has(id))errors.push('Missing wireframe anchor '+id);
if(errors.length){console.error(errors.join('\n'));process.exitCode=1}else console.log(`Documentation passed: ${md.length} Markdown files, ${links} local links, 6 specs, 18 sections/94 fields, valid JSON and 12 wireframe anchors.`);
