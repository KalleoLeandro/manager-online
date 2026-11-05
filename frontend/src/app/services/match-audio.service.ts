import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { MatchView } from './matchday.service';
import { readLocal, writeLocal } from '../utils/local-storage';
import { currentGoal } from '../utils/goal-celebration';

type Sound='crowd'|'goal'|'boo';
@Injectable({providedIn:'root'})
export class MatchAudioService {
 readonly enabled=signal(readLocal<boolean>('match-audio-enabled')??true);
 readonly waitingGesture=signal(false);
 readonly ready=signal(false);readonly loading=signal(false);readonly error=signal('');
 readonly volume=signal(Math.max(0,Math.min(1,Number(readLocal<number>('match-audio-volume')??.55))));
 private context?:AudioContext;private master?:GainNode;private ambience?:AudioBufferSourceNode;private effect?:AudioBufferSourceNode;
 private effectType?:Sound;private buffers=new Map<Sound,AudioBuffer>();private match:MatchView|null=null;
 private fixture:number|null=null;private lastSequence=0;private heardGoals=new Set<string>();private disposed=false;
 constructor(){
  const visibility=()=>{if(document.hidden)this.stopSources();else {this.sync();if(this.enabled()&&this.match)void this.activate();}};
  const unlock=()=>{if(this.enabled()&&this.match)void this.activate();};
  document.addEventListener('visibilitychange',visibility);document.addEventListener('pointerdown',unlock);document.addEventListener('keydown',unlock);
  inject(DestroyRef).onDestroy(()=>{this.disposed=true;document.removeEventListener('visibilitychange',visibility);document.removeEventListener('pointerdown',unlock);document.removeEventListener('keydown',unlock);this.stopSources();if(this.context)this.context.onstatechange=null;void this.context?.close();});
 }
 async toggle():Promise<void>{
  if(this.enabled()){this.enabled.set(false);this.stopSources();this.persist('match-audio-enabled',false);return;}
  this.enabled.set(true);this.persist('match-audio-enabled',true);await this.activate();
 }
 private async activate():Promise<void>{
  if(this.disposed||!this.enabled())return;
  if(this.loading()){void this.context?.resume().catch(()=>this.waitingGesture.set(true));return;}
  try{
   this.context??=new AudioContext();const context=this.context;
   context.onstatechange=()=>{this.waitingGesture.set(context.state!=='running');if(!this.disposed)this.sync();};
   // Autoplay may remain suspended until a gesture. Never block asset loading on resume().
   this.waitingGesture.set(context.state!=='running');void context.resume().catch(()=>this.waitingGesture.set(true));
   if(this.ready()){this.sync();return;}
   this.loading.set(true);this.error.set('');
   if(!this.master){this.master=context.createGain();this.master.connect(context.destination);}
   this.master.gain.value=this.volume();
   await Promise.all((['crowd','goal','boo'] as Sound[]).map(async type=>{if(this.buffers.has(type))return;const response=await fetch(`/assets/audio/${type==='goal'?'goal.mp3':type+'.wav'}?v=goal-torcida-3`);if(!response.ok)throw Error('audio');this.buffers.set(type,await context.decodeAudioData(await response.arrayBuffer()));}));
   if(this.disposed)return;
   this.ready.set(true);this.sync();
  }catch{this.ready.set(false);this.enabled.set(false);this.error.set('N\u00e3o foi poss\u00edvel ativar o som. Toque para tentar novamente.');this.stopSources();}
  finally{this.loading.set(false);}
 }
 setVolume(value:number):void{const volume=Number.isFinite(value)?Math.max(0,Math.min(1,value)):.55;this.volume.set(volume);this.persist('match-audio-volume',volume);if(this.context&&this.master)this.master.gain.setTargetAtTime(volume,this.context.currentTime,.05);}
 observe(match:MatchView|null|undefined):void{
  if(!match){this.stopMatch();return;}
  const changed=this.fixture!==match.fixtureId;if(changed){this.stopSources();this.fixture=match.fixtureId;this.lastSequence=Math.max(0,...match.events.map(e=>e.sequence));}
  const cards=!changed&&match.events.some(e=>e.sequence>this.lastSequence&&(e.type==='YELLOW'||e.type==='RED'));
  this.lastSequence=Math.max(this.lastSequence,...match.events.map(e=>e.sequence));this.match=match;this.sync();if(this.enabled()&&!this.context)void this.activate();
  const goal=this.playGoal(changed);if(cards&&!goal&&match.status!=='FINISHED')this.playEffect('boo');
 }
 stopMatch():void{this.match=null;this.fixture=null;this.lastSequence=0;this.stopSources();}
 private canPlay():boolean{return !!this.context&&this.context.state==='running'&&this.enabled()&&this.ready()&&!document.hidden;}
 private sync():void{
  const m=this.match;
  if(this.effectType==='goal'&&(!m||m.status!=='LIVE'||!!m.varReview))this.stopEffect();
  const playing=this.canPlay()&&m?.status==='LIVE'&&!m.varReview&&!(m.goalCelebrationRemainingMillis>0);
  if(!playing){this.stopAmbience();return;}
  if(!this.ambience){const source=this.source('crowd',.6);if(source){source.loop=true;source.start();this.ambience=source;}}
 }
 private playGoal(initialSnapshot:boolean):boolean{
  const m=this.match,goal=currentGoal(m);if(!m||m.status!=='LIVE'||!goal)return false;
  const key=goal.key;
  if(this.heardGoals.has(key))return this.effectType==='goal';
  this.heardGoals.add(key);if(this.heardGoals.size>2000)this.heardGoals=new Set([...this.heardGoals].slice(-500));
  // Opening a fixture, enabling sound or returning from tactics must not replay an old goal.
  // Consume the event even when muted: late asset loading must never trigger a delayed shout.
  if(initialSnapshot||!this.canPlay())return false;
  this.playEffect('goal');return true;
 }
 private source(type:Sound,gain:number):AudioBufferSourceNode|null{
  const context=this.context,buffer=this.buffers.get(type);if(!context||!buffer||!this.master)return null;
  const source=context.createBufferSource(),level=context.createGain();source.buffer=buffer;level.gain.value=gain;source.connect(level);level.connect(this.master);
  source.onended=()=>{source.disconnect();level.disconnect();};return source;
 }
 private playEffect(type:'goal'|'boo'):void{
  if(!this.canPlay()||type==='boo'&&this.effectType==='goal')return;
  this.effect?.stop();const source=this.source(type,type==='goal'?1:.65);if(!source)return;
  const cleanup=source.onended;source.onended=event=>{cleanup?.call(source,event);if(this.effect===source){this.effect=undefined;this.effectType=undefined;}};
  this.effect=source;this.effectType=type;source.start();
 }
 private stopAmbience():void{this.ambience?.stop();this.ambience=undefined;}
 private stopEffect():void{this.effect?.stop();this.effect=undefined;this.effectType=undefined;}
 private stopSources():void{this.stopAmbience();this.stopEffect();}
 private persist(key:string,value:unknown):void{try{writeLocal(key,value);}catch{/* Audio still works when storage is unavailable. */}}
}
