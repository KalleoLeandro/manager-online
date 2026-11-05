import { Component, computed, input, output } from '@angular/core';
import { MatchTeam, MatchView, TouchlineCommand } from '../../services/matchday.service';
import { GameIcon } from '../game-icon/game-icon';

@Component({selector:'app-match-touchline',imports:[GameIcon],templateUrl:'./match-touchline.html',styleUrl:'./match-touchline.css'})
export class MatchTouchline {
 readonly match=input.required<MatchView>();
 readonly team=input<MatchTeam|null>(null);
 readonly busy=input(false);
 readonly commanded=output<TouchlineCommand>();
 readonly state=computed(()=>this.team()?.touchline);
 readonly elapsed=computed(()=>{const m=this.match();return m.minute+(m.period===2?m.addedFirst:0);});
 readonly cooldown=computed(()=>Math.max(0,(this.state()?.cooldownUntil||0)-this.elapsed()));
 readonly remaining=computed(()=>Math.max(0,(this.state()?.effectUntil||0)-this.elapsed()));
 readonly blocked=computed(()=>this.busy()||!this.team()||this.match().status!=='LIVE'||!!this.match().varReview||this.match().goalCelebrationRemainingMillis>0||!!this.state()?.dismissed||this.cooldown()>0);
 readonly commands:{type:TouchlineCommand;label:string;icon:string;hint:string}[]=[
  {type:'ENCOURAGE',label:'Incentivar',icon:'trophy',hint:'Pequeno estímulo ao ataque por 5 minutos.'},
  {type:'DEMAND',label:'Cobrar intensidade',icon:'arrow',hint:'Mais pressão por 5 minutos, com maior desgaste.'},
  {type:'CALM',label:'Acalmar',icon:'details',hint:'Mais controle e menos ímpeto ofensivo por 5 minutos.'},
  {type:'COMPLAIN',label:'Reclamar',icon:'whistle',hint:'Reação: +2% ataque e pressão, +5% desgaste por 5 min. Custa 30 pontos de relação e pode causar expulsão.'}
 ];
 readonly activeLabel=computed(()=>this.commands.find(c=>c.type===this.state()?.command)?.label||'');
}
