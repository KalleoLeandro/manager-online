// Run: npm install --prefix .tools/trophy-assets sharp
// Then: node backend/tools/prepare-trophy-assets.mjs
import fs from 'node:fs/promises';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const sharp = require('../../.tools/trophy-assets/node_modules/sharp');
const directory = new URL('../src/main/resources/res/trophies/', import.meta.url);
await fs.mkdir(directory, { recursive: true });
const assets = [
 [1,'Nacional genérico',null,'cup'],[2,'Copa genérica',null,'cup'],[3,'Internacional genérico',null,'cup'],
 [10,'Brasileirão Série A','https://images.seeklogo.com/logo-png/57/2/taca-brasileirao-serie-a-logo-png_seeklogo-577657.png','cup'],
 [11,'Premier League','Premier league trophy icon (adjusted).png','cup'],
 [12,'La Liga','Liga trophy (adjusted).png','cup'],
 [13,'Serie A italiana',"Coppa Campioni d'Italia (Serie A).png",'cup'],
 [14,'Ligue 1','Ligue 1 Trophy 2024.png','cup'],
 [15,'Bundesliga','Meisterschale.png','shield'],
 [20,'Copa do Brasil','CopadoBrasil2013-.png','cup'],
 [21,'FA Cup','FA Cup.png','cup'],
 [22,'Copa del Rey','Copa del Rey Trophy.png','cup'],
 [23,'Coppa Italia','Coppa Italia (Italy Cup).svg','cup'],
 [24,'Coupe de France','Coupe de France trophy.png','cup'],
 [25,'DFB-Pokal','DFB Pokal Trophy.png','cup'],
 [30,'Libertadores','https://images.seeklogo.com/logo-png/57/2/taca-libertadores-da-america-logo-png_seeklogo-577675.png','cup'],
 [31,'Champions League','Coppa Campioni.svg','cup'],
 [40,'Bola de ouro','Balón de oro.png','ball'],
 [41,'Chuteira de ouro','Bota de oro.png','boot'],[42,'Luva de ouro','Golden Glove.png','glove']
];
function illustration(kind){
 const shape=kind==='ball'?'<circle cx="160" cy="144" r="84"/><path d="m160 100 41 30-16 48h-50l-16-48z" fill="#96702c"/>':kind==='shield'?'<circle cx="160" cy="170" r="112"/><circle cx="160" cy="170" r="76" fill="#b6c3cb"/><circle cx="160" cy="170" r="35"/>':kind==='boot'?'<path d="M98 94h65l10 92 85 42q32 38-10 48H67q-30-4-22-35l37-72z"/><path d="m161 189-48 12m68 2-47 13m70 1-45 14" stroke="#715421"/>':kind==='glove'?'<path d="M98 271 78 183q-8-27 12-30l20 25V76q0-27 21-17l9 98V58q12-27 25-3l4 100 8-88q16-20 26 4l-6 99 14-62q19-14 24 9l-16 111-28 43z"/>':'<path d="M101 76H52v57q0 65 65 70m102-127h49v57q0 65-65 70" fill="none" stroke="url(#metal)" stroke-width="18"/><path d="M91 56h138l-13 116q-5 45-45 57v48h43v24H106v-24h43v-48q-40-12-45-57z"/>';
 return `<svg xmlns="http://www.w3.org/2000/svg" width="320" height="360" viewBox="0 0 320 360"><defs><linearGradient id="metal"><stop stop-color="#957036"/><stop offset=".45" stop-color="#fff0b1"/><stop offset="1" stop-color="#b4873f"/></linearGradient></defs><g fill="url(#metal)" stroke="#8c6b35" stroke-width="3" stroke-linejoin="round">${shape}<path d="M87 309h146v25H87z" fill="#33413e"/></g></svg>`;
}
const version=2;
const directCredits={
 10:{source:'https://seeklogo.com/vector-logo/577657/taca-brasileirao-serie-a',author:'Belchior Novais',license:'Direitos do respectivo titular; licença aberta não informada na fonte'},
 30:{source:'https://seeklogo.com/vector-logo/577675/taca-libertadores-da-america',author:'Belchior Novais',license:'Direitos do respectivo titular; licença aberta não informada na fonte'}
};
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function download(url){
 for(let attempt=0;attempt<3;attempt++){
  try{
   const response=await fetch(url,{headers:{'User-Agent':'Mozilla/5.0 (compatible; ManagerOnlineAssets/2.0)',...(url.includes('seeklogo.com')?{Referer:'https://seeklogo.com/'}:{})},signal:AbortSignal.timeout(30000)});
   if(!response.ok)throw Error(`HTTP ${response.status}: ${url}`);
   return Buffer.from(await response.arrayBuffer());
  }catch(error){if(attempt===2)throw error;await delay(3000*(attempt+1));}
 }
}
// Crop transparent padding only. Never remove white/silver colors from the trophy.
async function normalize(source,requireTransparency){
 const {data,info}=await sharp(source).toColourspace('srgb').ensureAlpha().raw().toBuffer({resolveWithObject:true});
 let left=info.width,top=info.height,right=-1,bottom=-1,transparent=0;
 for(let y=0;y<info.height;y++)for(let x=0;x<info.width;x++){
  const alpha=data[(y*info.width+x)*4+3];
  if(alpha<8)transparent++;
  if(alpha>4){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
 }
 if(right<left)throw Error('Imagem sem conteúdo visível');
 if(requireTransparency&&transparent/(info.width*info.height)<.01)throw Error('Fonte sem fundo transparente');
 return sharp(source).extract({left,top,width:right-left+1,height:bottom-top+1})
  .resize({width:304,height:344,fit:'contain',background:'#00000000'})
  .extend({top:8,bottom:8,left:8,right:8,background:'#00000000'}).png().toBuffer();
}
const previous=await fs.readFile(new URL('credits.json',directory),'utf8').then(JSON.parse).catch(()=>[]);
const credits=new Map(previous.map(credit=>[credit.id,credit]));
const selected=process.argv.find(arg=>arg.startsWith('--ids='))?.slice(6).split(',').map(Number);
const pending=[];
for(const asset of assets){
 const [id,,file]=asset;if(selected&&!selected.includes(id))continue;
 const current=credits.get(id);
 const exists=await fs.access(new URL(`${id}.png`,directory)).then(()=>true).catch(()=>false);
 if(exists&&current?.preparationVersion===version&&current?.assetKey===file&&!process.argv.includes('--refresh')&&!process.argv.includes('--illustrations-only'))continue;
 pending.push(asset);
}
// Batch metadata to avoid one Commons API request for every image.
const commonsFiles=pending.map(asset=>asset[2]).filter(file=>file&&!file.startsWith('https:'));
const metadata=new Map();
if(commonsFiles.length&&!process.argv.includes('--illustrations-only')){
 const api=new URL('https://commons.wikimedia.org/w/api.php');
 api.search=new URLSearchParams({action:'query',format:'json',titles:commonsFiles.map(file=>'File:'+file).join('|'),prop:'imageinfo',iiprop:'url|extmetadata'});
 try{const response=JSON.parse((await download(api.href)).toString('utf8'));for(const page of Object.values(response.query.pages))metadata.set(page.title.slice(5),page.imageinfo?.[0]);}
 catch(error){console.warn('Metadados indisponíveis: '+error.message);}
}
let failed=0;
for(const [id,name,file,kind] of pending){
 const target=new URL(`${id}.png`,directory);
 try{
  let source,credit={id,name,source:'Ilustração original do projeto',license:'MIT',illustration:true};
  if(file&&!process.argv.includes('--illustrations-only')){
   const direct=file.startsWith('https:');const info=direct?null:metadata.get(file);
   if(!direct&&!info)throw Error('Metadados do arquivo não encontrados');
   source=await download(direct?file:info.url);
   credit={id,name,...(direct?directCredits[id]:{source:info.descriptionurl,author:info.extmetadata?.Artist?.value,license:info.extmetadata?.LicenseShortName?.value,licenseUrl:info.extmetadata?.LicenseUrl?.value}),illustration:false,
    changes:'Margens transparentes recortadas; escala e alinhamento padronizados em PNG transparente. Arte preservada.'};
  }else source=Buffer.from(illustration(kind));
  const png=await normalize(source,!credit.illustration);
  // Fully decode and validate before replacing an existing asset.
  await fs.writeFile(target,png);
  credits.set(id,{...credit,assetKey:file,preparationVersion:version});
  console.log(`${id}.png: ${name}${credit.illustration?' (reserva genérica)':' (transparente)'}`);
 }catch(error){failed++;console.warn(`${id}.png: ${error.message}. Mantido o arquivo existente, se houver.`);}
 if(file)await delay(1600);
}
await fs.writeFile(new URL('credits.json',directory),JSON.stringify([...credits.values()].sort((a,b)=>a.id-b.id),null,2));
if(await fs.access(new URL('1.png',directory)).then(()=>true).catch(()=>false))
 await fs.copyFile(new URL('1.png',directory),new URL('../src/main/resources/res/trophy-generic.png',import.meta.url));
if(failed)process.exitCode=1;
