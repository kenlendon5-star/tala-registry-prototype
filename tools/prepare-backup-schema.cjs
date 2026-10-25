// Generates the offline backup contract and example. D17 amendment: every field the
// native Android store does not persist was removed rather than synthesized — record
// revision/createdAt/updatedAt, member name parts, interview reviewedAt/reviewedRevision,
// and location confirmedAt/addressAtCapture/notes. Visits carry both visitedOn (the
// form-entered date) and recordedAt (append order) instead of one synthesized timestamp.
const fs=require('node:fs'),path=require('node:path');const dir=path.resolve(__dirname,'../planning/docs/schemas');fs.mkdirSync(dir,{recursive:true});
const ref=n=>({$ref:'#/definitions/'+n}),str={type:'string'},integer={type:'integer',minimum:0},nullable=t=>({anyOf:[t,{type:'null'}]});
const obj=(properties,required=Object.keys(properties))=>({type:'object',additionalProperties:false,properties,required});
const list=(items,maxItems=100000)=>({type:'array',items,maxItems});
const definitions={
 uuid:{type:'string',pattern:'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'},
 timestamp:{type:'string',format:'date-time',pattern:'Z$'},
 scalar:obj({kind:{const:'scalar'},value:str}),multi:obj({kind:{const:'multi'},value:{type:'array',items:str,uniqueItems:true,maxItems:100}}),
 answer:obj({section:{type:'string',minLength:1},field:{type:'string',minLength:1},memberId:nullable(ref('uuid')),answer:{oneOf:[ref('scalar'),ref('multi')]}}),
 member:obj({id:ref('uuid'),displayName:str,sortOrder:integer}),
 visit:obj({id:ref('uuid'),visitedOn:str,outcome:{enum:['Interview started','Completed visit','Callback','No respondent','Refused']},callbackLocalDateTime:nullable({type:'string',pattern:'^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$'}),callbackTimeZone:nullable({type:'string',minLength:1}),notes:str,recordedAt:nullable(ref('timestamp'))}),
 location:obj({latitude:{type:'number',minimum:-90,maximum:90},longitude:{type:'number',minimum:-180,maximum:180},accuracyMetres:nullable({type:'number',minimum:0}),source:{enum:['device','manual']},capturedAt:ref('timestamp')}),
 attachment:obj({id:ref('uuid'),memberId:nullable(ref('uuid')),kind:{enum:['household-photo','member-photo','signature']},mimeType:{enum:['image/jpeg','image/png','image/webp']},byteSize:{type:'integer',minimum:1,maximum:5242880},sha256:{type:'string',pattern:'^[a-f0-9]{64}$'},contentBase64:{type:'string',minLength:4,maxLength:6990508,pattern:'^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$'}}),
 interview:obj({id:ref('uuid'),questionnaireVersion:{type:'string',minLength:1},consent:{enum:['Unanswered','Yes','No']},signedName:nullable(str),signatureAttachmentId:nullable(ref('uuid')),answers:list(ref('answer'))}),
};
definitions.record=obj({id:ref('uuid'),displayCode:{type:'string',minLength:1},headName:str,address:str,status:{enum:['NotStarted','InProgress','Callback','Reviewed']},archivedAt:nullable(ref('timestamp')),members:list(ref('member'),1000),interview:ref('interview'),visits:list(ref('visit'),10000),location:nullable(ref('location')),attachments:list(ref('attachment'),1000)});
const schema={$schema:'http://json-schema.org/draft-07/schema#',title:'Tala offline backup v1',description:'Documentation contract for the native Android export (D17). Fields the record store does not persist are absent; semantic ownership, uniqueness, questionnaire validation and media checks are additional restore-side requirements.',...obj({format:{const:'tala-backup'},formatVersion:{const:1},exportedAt:ref('timestamp'),prototype:{const:true},records:list(ref('record'),10000)}),definitions};
const example={format:'tala-backup',formatVersion:1,exportedAt:'2026-10-11T09:00:00Z',prototype:true,records:[{id:'11111111-1111-4111-8111-111111111111',displayCode:'HH-00001',headName:'Alex Reyes',address:'Fictional Demo Street',status:'InProgress',archivedAt:null,members:[{id:'22222222-2222-4222-8222-222222222222',displayName:'Alex Reyes',sortOrder:0}],interview:{id:'33333333-3333-4333-8333-333333333333',questionnaireVersion:'tala-demo-v1',consent:'Unanswered',signedName:null,signatureAttachmentId:null,answers:[{section:'demographics',field:'registeredVoter',memberId:'22222222-2222-4222-8222-222222222222',answer:{kind:'scalar',value:'No'}}]},visits:[],location:null,attachments:[]}]};
for(const [name,value] of [['backup-v1.schema.json',schema],['backup-v1.example.json',example]])fs.writeFileSync(path.join(dir,name),JSON.stringify(value,null,2)+'\n');
console.log('Generated offline backup schema and fictional draft example.');
