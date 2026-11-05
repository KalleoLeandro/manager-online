import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';

// Build-time only. The game serves the resulting WAVs locally.
const root=fileURLToPath(new URL('../../',import.meta.url));
const cache=path.join(root,'.tools/audio-sources'),out=path.join(root,'frontend/src/assets/audio');
const binary=process.argv.includes('--ffmpeg')?path.resolve(process.argv[process.argv.indexOf('--ffmpeg')+1]):'ffmpeg';
const sources=[
 {name:'crowd',id:39733,author:'huubjeroen',title:'noise#01.aif',url:'https://cdn.freesound.org/previews/39/39733_190760-hq.mp3',page:'https://freesound.org/people/huubjeroen/sounds/39733/',start:45,duration:31,loudness:-19,loop:true,energetic:true},
 {name:'goal',id:346685,author:'deleted_user_2104797',title:'Crowd Cheer.wav',url:'https://cdn.freesound.org/previews/346/346685_2104797-hq.mp3',page:'https://freesound.org/people/deleted_user_2104797/sounds/346685/',start:1,duration:4.8,loudness:-12,energetic:true},
 {name:'boo',id:504621,author:'NeoSpica',title:'Booing Crowd',url:'https://cdn.freesound.org/previews/504/504621_7704891-hq.mp3',page:'https://freesound.org/people/NeoSpica/sounds/504621/',start:1,duration:3.2,loudness:-17},
];
await fs.mkdir(cache,{recursive:true});await fs.mkdir(out,{recursive:true});
const manifest=[];
const voice={id:219873,title:'goal shout.mp3',author:'polmdx',source:'https://freesound.org/people/polmdx/sounds/219873/',download:'https://cdn.freesound.org/previews/219/219873_4071086-hq.mp3',license:'CC-BY-4.0',licenseUrl:'https://creativecommons.org/licenses/by/4.0/'};
async function cached(id,url){const input=path.join(cache,`${id}-hq.mp3`);try{await fs.access(input);}catch{const response=await fetch(url);if(!response.ok)throw Error(`HTTP ${response.status}`);const temporary=input+'.download';await fs.writeFile(temporary,Buffer.from(await response.arrayBuffer()));await fs.rename(temporary,input);}return input;}
function energeticStart(input,duration){
 const decoded=spawnSync(binary,['-v','error','-i',input,'-ar','8000','-ac','1','-f','s16le','-'],{maxBuffer:20*1024*1024,windowsHide:true});if(decoded.status!==0)throw Error(decoded.stderr.toString());
 const pcm=decoded.stdout,count=pcm.length/2,prefix=new Float64Array(count+1);for(let i=0;i<count;i++){const sample=pcm.readInt16LE(i*2)/32768;prefix[i+1]=prefix[i]+sample*sample;}
 const window=Math.round(duration*8000);let best=0,energy=-1;for(let i=0;i+window<=count;i+=2000){const value=prefix[i+window]-prefix[i];if(value>energy){energy=value;best=i;}}return best/8000;
}
for(const source of sources){
 const input=await cached(source.id,source.url),output=path.join(out,`${source.name}.wav`);
 if(source.energetic)source.start=energeticStart(input,source.duration);
 const filter=`highpass=f=65,lowpass=f=11000,loudnorm=I=${source.loudness}:TP=-2:LRA=9`+(source.loop?'':`,afade=t=in:d=0.08,afade=t=out:st=${source.duration-.4}:d=0.4`);
 const args=['-hide_banner','-loglevel','error','-y','-ss',String(source.start),'-i',input];
 let voiceInput;
 if(source.name==='goal'){
  voiceInput=await cached(voice.id,voice.download);args.push('-i',voiceInput,'-filter_complex',`[0:a]atrim=duration=4.8,asetpts=PTS-STARTPTS,highpass=f=65,lowpass=f=11000,loudnorm=I=-18:TP=-2:LRA=7,volume=0.65[crowd];[1:a]silenceremove=start_periods=1:start_threshold=-38dB:start_silence=0.03,asetpts=PTS-STARTPTS,highpass=f=100,lowpass=f=9000,loudnorm=I=-14:TP=-2:LRA=7,volume=1.2,apad=whole_dur=4.8[voice];[crowd][voice]amix=inputs=2:duration=longest:normalize=0,loudnorm=I=-12:TP=-1.5:LRA=7,volume=1.4,alimiter=limit=0.88:level=false,afade=t=in:d=0.025,afade=t=out:st=4.4:d=0.4[mix]`,'-map','[mix]');
 }else args.push('-af',filter);
 args.push('-t',String(source.duration),'-ar','24000','-ac','2','-c:a','pcm_s16le','-fflags','+bitexact','-flags:a','+bitexact',output);
 const result=spawnSync(binary,args,{encoding:'utf8',windowsHide:true});
 if(result.error||result.status!==0)throw Error(result.error?.message||result.stderr);
 if(source.loop){
  const wav=await fs.readFile(output);let at=12,dataAt=-1;
  while(at+8<=wav.length){const size=wav.readUInt32LE(at+4);if(wav.toString('ascii',at,at+4)==='data'){dataAt=at;break;}at+=8+size+(size%2);}
  if(dataAt<0)throw Error('Missing PCM data');
  const pcm=wav.subarray(dataAt+8),blend=24000*2*2;
  for(let offset=0;offset<blend;offset+=2){const fraction=Math.floor(offset/4)/(24000-1),tail=pcm.length-blend+offset;pcm.writeInt16LE(Math.round(pcm.readInt16LE(tail)*(1-fraction)+pcm.readInt16LE(offset)*fraction),tail);}
  const samples=pcm.subarray(blend),header=Buffer.from(wav.subarray(0,dataAt+8));header.writeUInt32LE(header.length+samples.length-8,4);header.writeUInt32LE(samples.length,dataAt+4);await fs.writeFile(output,Buffer.concat([header,samples]));
 }
 const contents=await fs.readFile(output);
 manifest.push({file:`${source.name}.wav`,title:source.name==='goal'?'Goal shout and crowd celebration':source.title,author:source.name==='goal'?source.author+' + polmdx':source.author,source:source.page,download:source.url,license:source.name==='goal'?'CC-BY-4.0':'CC0-1.0',licenseUrl:source.name==='goal'?voice.licenseUrl:'https://creativecommons.org/publicdomain/zero/1.0/',...(voiceInput?{voice:{...voice,sourceSha256:createHash('sha256').update(await fs.readFile(voiceInput)).digest('hex')}}:{}),sourceSha256:createHash('sha256').update(await fs.readFile(input)).digest('hex'),sha256:createHash('sha256').update(contents).digest('hex'),changes:`${source.energetic?'Most energetic window selected; ':''}Excerpt from ${source.start}s; ${source.loop?'30s loop with 1s crossfade':source.duration+'s with fades'}; filtering, loudness normalization${voiceInput?', voice trimmed, padded and mixed with cheering crowd, +2.9dB lift with 0.88 peak limiter':''}, stereo PCM16 24kHz`});
 console.log(`${source.name}.wav: ${contents.length} bytes`);
}
await fs.writeFile(path.join(out,'sources.json'),JSON.stringify(manifest,null,2)+'\n');
await fs.writeFile(path.join(out,'CREDITS.txt'),`MATCH AUDIO CREDITS\n\nGoal shout: "goal shout.mp3" by polmdx\n${voice.source}\nCreative Commons Attribution 4.0: ${voice.licenseUrl}\nChanges: silence trimming, filtering, normalization and mixing with cheering crowd.\n\nGoal crowd: "Crowd Cheer.wav" by deleted_user_2104797\nhttps://freesound.org/people/deleted_user_2104797/sounds/346685/\nCC0 1.0, excerpt and normalization.\n\nStadium ambience: "noise#01.aif" by huubjeroen\nhttps://freesound.org/people/huubjeroen/sounds/39733/\nCC0 1.0, energetic excerpt, filtering, normalization and loop crossfade.\n\nBooing: "Booing Crowd" by NeoSpica\nhttps://freesound.org/people/NeoSpica/sounds/504621/\nCC0 1.0, excerpt, filtering, normalization and fades.\n\nCC0 license: https://creativecommons.org/publicdomain/zero/1.0/\nNo endorsement by the original authors is implied.\n`);
