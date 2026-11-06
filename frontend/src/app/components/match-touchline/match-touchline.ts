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
 readonly relationshipLabel=computed(()=>{const value=this.state()?.refereeConfidence??100;return value>70?'Boa':value>45?'Regular':'Ruim';});
 readonly commands:{type:TouchlineCommand;label:string;icon:string;hint:string}[]=[
  {type:'ENCOURAGE',label:'Incentivar',icon:'trophy',hint:'Estimule a equipe a buscar o ataque.'},
  {type:'DEMAND',label:'Cobrar intensidade',icon:'arrow',hint:'Exija mais pressão, com maior desgaste.'},
  {type:'CALM',label:'Acalmar',icon:'details',hint:'Peça mais controle e menos ímpeto ofensivo.'},
  {type:'COMPLAIN',label:'Reclamar',icon:'whistle',hint:'Tente provocar uma reação da equipe. Pode irritar o árbitro e causar expulsão.'}
 ];
 readonly activeLabel=computed(()=>this.commands.find(c=>c.type===this.state()?.command)?.label||'');
}
