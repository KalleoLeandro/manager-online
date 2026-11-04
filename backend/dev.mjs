import {watch} from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';

const backend=import.meta.dirname;
const javaSource=path.join(backend,'src/main/java');
const resources=path.join(backend,'src/main/resources');
const classes=path.join(backend,'target/classes');
const children=new Set(),watchers=[];
let stopping=false,compiling=false,pending=false,timer;

function maven(args){
  // Fixed arguments only; paths and source filenames are never interpolated into shell commands.
  const child=process.platform==='win32'
    ?spawn(process.env.ComSpec||'cmd.exe',['/d','/s','/c',`mvnw.cmd ${args.map(a=>`"${a}"`).join(' ')}`],{cwd:backend,stdio:'inherit',windowsHide:true,windowsVerbatimArguments:true})
    :spawn('./mvnw',args,{cwd:backend,stdio:'inherit'});
  children.add(child);child.once('close',()=>children.delete(child));return child;
}

function completion(child){return new Promise((resolve,reject)=>{child.once('error',reject);child.once('close',code=>resolve(code??1));});}

async function removeOrphanClasses(folder=classes){
  for(const entry of await fs.readdir(folder,{withFileTypes:true}).catch(error=>{if(error.code==='ENOENT')return [];throw error;})){
    const file=path.join(folder,entry.name);
    if(entry.isDirectory()){await removeOrphanClasses(file);continue;}
    if(!entry.name.endsWith('.class'))continue;
    const relative=path.relative(classes,file).replace(/\$[^/\\]*\.class$/,'.class').replace(/\.class$/,'.java');
    try{await fs.access(path.join(javaSource,relative));}catch(error){
      if(error.code!=='ENOENT')throw error;
      if(!file.startsWith(classes+path.sep))throw Error('Classe fora do diretório de compilação');
      await fs.unlink(file);
    }
  }
}

async function compile(){
  await removeOrphanClasses();
  const code=await completion(maven(['-B','-q','compile']));
  if(code!==0){console.error('[dev] Compilação falhou. Corrija o código e salve novamente.');return false;}
  // DevTools checks changes only after compilation has finished successfully.
  await fs.mkdir(classes,{recursive:true});
  await fs.writeFile(path.join(classes,'.reloadtrigger'),String(Date.now()));
  return true;
}

async function rebuild(){
  if(stopping||compiling)return;
  compiling=true;
  try{
    while(pending&&!stopping){pending=false;console.log('[dev] Alteração detectada; recompilando...');if(await compile())console.log('[dev] Compilação concluída; DevTools verificará a reinicialização.');}
  }catch(error){console.error('[dev]',error.message);}
  finally{compiling=false;}
}

function changed(filename,resource=false){
  if(stopping)return;
  const name=String(filename||'').replaceAll('\\','/');
  if(resource&&/^res\/(players|crests)(\/|$)/.test(name))return;
  if(!resource&&name&&!name.endsWith('.java'))return;
  pending=true;clearTimeout(timer);timer=setTimeout(rebuild,500);
}

async function stop(){
  if(stopping)return;stopping=true;clearTimeout(timer);watchers.forEach(w=>w.close());
  await Promise.all([...children].filter(child=>child.pid).map(child=>new Promise(resolve=>{
    if(process.platform==='win32'){
      const killer=spawn('taskkill',['/PID',String(child.pid),'/T','/F'],{stdio:'ignore',windowsHide:true});
      killer.once('error',resolve);killer.once('close',resolve);
    }else{child.kill('SIGTERM');resolve();}
  })));
}

for(const signal of ['SIGINT','SIGTERM'])process.once(signal,()=>{void stop().then(()=>{process.exitCode=0;});});

try{
  console.log('[dev] Preparando backend com Spring Boot DevTools...');
  if(!await compile())process.exitCode=1;
  else if(!stopping){
    watchers.push(watch(javaSource,{recursive:true},(_,file)=>changed(file)));
    watchers.push(watch(resources,{recursive:true},(_,file)=>changed(file,true)));
    watchers.push(watch(path.join(backend,'../database'),(_,file)=>{if(String(file)==='init.db')changed(file,true);}));
    console.log('[dev] Java e configurações serão recompilados automaticamente. Ctrl+C encerra somente este backend.');
    const code=await completion(maven(['-B','spring-boot:run','-Dspring-boot.run.arguments=--spring.devtools.restart.trigger-file=.reloadtrigger']));
    if(!stopping)process.exitCode=code;
  }
}catch(error){console.error('[dev]',error.message);process.exitCode=1;}
finally{await stop();}
