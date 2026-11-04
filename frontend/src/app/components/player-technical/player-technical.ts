import { Component, computed, input, signal } from '@angular/core';
import { AttributeScore } from '../attribute-score/attribute-score';
import { PlayerCondition, PlayerTechnical, attributeDefinitions } from '../../utils/player-attributes';

@Component({selector:'app-player-technical',imports:[AttributeScore],templateUrl:'./player-technical.html',styleUrl:'./player-technical.css'})
export class PlayerTechnicalDetails {
  readonly technical=input<PlayerTechnical|null>(null);
  readonly condition=input<PlayerCondition|null>(null);
  readonly position=input('MEI');
  readonly view=signal<'attributes'|'condition'>('attributes');
  private readonly chosen=signal<string|null>(null);
  readonly group=computed(()=>this.chosen()||(this.position()==='GOL'?'goalkeeping':'technical'));
  readonly groups=[{id:'technical',name:'Técnicos'},{id:'mental',name:'Mentais'},{id:'physical',name:'Físicos'},{id:'set_pieces',name:'Bolas paradas'},{id:'goalkeeping',name:'Goleiro'}];
  readonly fields=computed(()=>attributeDefinitions.filter(a=>a.group===this.group()));
  readonly sources=computed(()=>new Set(this.technical()?.sourcedAttributes||[]));
  readonly status=computed(()=>({AVAILABLE:'Apto para jogar',TIRED:'Desgastado',UNFIT:'Sem condição de jogo',INJURED:'Lesionado'}[this.condition()?.status||'AVAILABLE']));
  readonly axes=computed(()=>[
    {name:this.position()==='GOL'?'Gol':'Ataque',keys:this.position()==='GOL'?['reflexes','handling','gk_positioning','one_on_ones','diving']:['finishing','shot_power','off_ball','dribbling']},
    {name:'Passe',keys:['short_passing','long_passing','vision','first_touch']},
    {name:'Defesa',keys:['tackling','marking','heading','anticipation']},
    {name:'Físico',keys:['acceleration','pace','strength','agility','stamina']},
    {name:'Mental',keys:['decisions','composure','concentration','teamwork','consistency']},
    {name:'B. paradas',keys:['free_kicks','corners','penalties']}
  ].map(axis=>({...axis,value:Math.round(axis.keys.reduce((sum,key)=>sum+(this.technical()?.attributes[key]||1),0)/axis.keys.length)})));
  readonly basePolygon=computed(()=>this.axes().map((a,i)=>this.point(a.value,i)).join(' '));
  readonly currentPolygon=computed(()=>this.axes().map((a,i)=>this.point(a.value*(this.condition()?.performanceFactor??1),i)).join(' '));
  point(value:number,index:number):string {const angle=-Math.PI/2+index*Math.PI/3;return `${110+Math.cos(angle)*76*value/100},${110+Math.sin(angle)*76*value/100}`;}
  label(index:number):{x:number;y:number}{const angle=-Math.PI/2+index*Math.PI/3;return {x:110+Math.cos(angle)*96,y:114+Math.sin(angle)*96};}
  value(key:string):number{return this.technical()?.attributes[key]||1;}
  current(key:string):number|null{return this.condition()?.available===false?null:Math.max(1,Math.round(this.value(key)*(this.condition()?.performanceFactor??1)));}
  selectGroup(group:string):void{this.chosen.set(group);}
}
