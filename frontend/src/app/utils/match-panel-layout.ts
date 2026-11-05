import { signal } from '@angular/core';
import { readLocal, writeLocal } from './local-storage';

export type MatchPanelId = 'commentary'|'scores'|'stats'|'lineups';
interface PanelPreference {visible:boolean;expanded:boolean}
const defaults:Record<MatchPanelId,PanelPreference>={commentary:{visible:true,expanded:true},scores:{visible:true,expanded:true},stats:{visible:true,expanded:true},lineups:{visible:true,expanded:true}};

/** Presentation only: hiding a panel never changes the match or discards a coaching form. */
export class MatchPanelLayout {
 private readonly key='match-panel-layout-v2';
 readonly state=signal(this.restore());
 readonly choices:{id:MatchPanelId;label:string;icon:string}[]=[{id:'commentary',label:'Lance a lance',icon:'messages'},{id:'scores',label:'Outros jogos',icon:'trophy'},{id:'stats',label:'Estatísticas',icon:'chart'},{id:'lineups',label:'Atletas',icon:'overview'}];
 visible(id:MatchPanelId):boolean{return this.state()[id].visible;}
 expanded(id:MatchPanelId):boolean{return this.state()[id].expanded;}
 toggle(id:MatchPanelId):void{this.update(id,{visible:!this.visible(id)});}
 hide(id:MatchPanelId):void{this.update(id,{visible:false});}
 expand(id:MatchPanelId,expanded:boolean):void{this.update(id,{expanded});}
 reveal(id:MatchPanelId):void{if(!this.visible(id)||!this.expanded(id))this.update(id,{visible:true,expanded:true});}
 reset():void{this.state.set(structuredClone(defaults));this.persist();}
 private update(id:MatchPanelId,change:Partial<PanelPreference>):void{this.state.update(s=>({...s,[id]:{...s[id],...change}}));this.persist();}
 private persist():void{try{writeLocal(this.key,this.state());}catch{/* Layout remains usable without storage. */}}
 private restore():Record<MatchPanelId,PanelPreference>{const saved=readLocal<Partial<Record<MatchPanelId,PanelPreference>>>(this.key);return Object.fromEntries(Object.entries(defaults).map(([id,value])=>{const preference=saved?.[id as MatchPanelId];return [id,{visible:typeof preference?.visible==='boolean'?preference.visible:value.visible,expanded:typeof preference?.expanded==='boolean'?preference.expanded:value.expanded}];})) as Record<MatchPanelId,PanelPreference>;}
}
