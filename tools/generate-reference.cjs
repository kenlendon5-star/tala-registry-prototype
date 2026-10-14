// Read-only extraction from the current questionnaire and exported Room schema.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const root=path.resolve(__dirname,'..'),out=path.join(root,'planning/docs/schemas');fs.mkdirSync(out,{recursive:true});
const prototype='planning/prototype as web app';
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const form=vm.runInNewContext(read(prototype+'/forms.js')+'\nFORM;');
const titles=vm.runInNewContext(read(prototype+'/app.js').match(/const sections=(\[[^\n]+?\]);/)[1]);
const sections=form.map((s,i)=>({number:i+1,route:s.route,title:titles[i],scope:s.person?'member':'household',fields:s.fields.map(f=>({key:f.key,label:f.label,type:f.type,options:Array.from(f.options),optional:!!f.optional,condition:f.when?f.when.toString():null,...Object.fromEntries(['min','max','past','hint'].filter(k=>f[k]!==undefined).map(k=>[k,f[k]]))}))}));
const inventory={description:'Current browser field inventory; conditional expressions are documentation, not an executable portable rules format.',source:prototype+'/forms.js',generatedFor:'2026-10-11',sections};
fs.writeFileSync(path.join(out,'questionnaire-current.json'),JSON.stringify(inventory,null,2)+'\n');
const cell=s=>String(s??'—').replace(/\|/g,'\\|').replace(/\n/g,' ');
let md='# Current questionnaire field dictionary\n\nGenerated from '+prototype+'/forms.js by tools/generate-reference.cjs. This is an implementation inventory, not an official questionnaire or approval of current defects. Date-dependent maxima follow the generation environment. Sections 4 and 18 are bespoke roster/consent workflows outside the field array.\n\n';
for(const s of sections){md+=`## ${s.number}. ${s.title} — ${s.scope}\n\nRoute: \`${s.route}\`\n\n`;if(!s.fields.length){md+='Managed by workflow.js rather than generic fields.\n\n';continue}md+='| Key | Label | Control | Required when visible | Options / bounds | Visibility |\n|---|---|---|---|---|---|\n';for(const f of s.fields)md+=`| ${f.key} | ${cell(f.label)} | ${f.type} | ${f.optional?'No':'Yes'} | ${cell(f.options.length?f.options.join('; '):[f.min!==undefined?'min '+f.min:'',f.max!==undefined?'max '+f.max:'',f.past?'not future':''].filter(Boolean).join('; ')||'—')} | ${cell(f.condition||'Always')} |\n`;md+='\n'}
md+='## Bespoke stored values\n\nConsent stores consent, drawn signature or signedName, and reviewedAt after review. The roster stores name/relationship and other member answers; IDs are currently household codes plus member array position. Browser location stores lat/lng, source, accuracy, capturedAt, verifiedAt, notes and addressAtCapture. Profile stores name, barangay and optional photo. These shapes are browser legacy data, not the proposed portable backup format.\n';
fs.writeFileSync(path.join(out,'questionnaire-field-dictionary.md'),md);
const room=JSON.parse(read('android-native/app/schemas/ph.tala.registry.data.local.RegistryDatabase/1.json'));
const ddl=['-- Generated from exported Room v1 schema. SQLite documentation snapshot only.','-- Do not apply this to Supabase/PostgreSQL. No schema changes are proposed here.','PRAGMA foreign_keys = ON;'];
for(const entity of room.database.entities){ddl.push(entity.createSql.replace(/\$\{TABLE_NAME\}/g,entity.tableName)+';');for(const index of entity.indices||[])ddl.push(index.createSql.replace(/\$\{TABLE_NAME\}/g,entity.tableName)+';')}
fs.writeFileSync(path.join(out,'room-v1.sql'),ddl.join('\n\n')+'\n');
console.log(`Generated ${sections.length} sections, ${sections.reduce((n,s)=>n+s.fields.length,0)} fields, and ${room.database.entities.length} Room tables.`);
