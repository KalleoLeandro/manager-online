import {PlayerExchange} from '../player-exchange/player-exchange';
import {formationRoles} from '../../utils/formations';
import { defaultTacticalPlan, TacticalPlan } from '../../utils/tactical-plan';
import { PositioningBoard } from '../positioning-board/positioning-board';
import { TeamStrategy } from '../team-strategy/team-strategy';
import { Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatchAction, MatchTeam } from '../../services/matchday.service';

export type CoachAction=Omit<MatchAction,'revision'>;
@Component({selector:'app-match-coach-controls',imports:[PlayerExchange,FormsModule,PositioningBoard,TeamStrategy],templateUrl:'./match-coach-controls.html',styleUrl:'./match-coach-controls.css'})
export class MatchCoachControls {
 readonly formationOptions=Object.keys(formationRoles);readonly team=input.required<MatchTeam>();readonly busy=input(false);readonly locked=input(false);readonly command=output<CoachAction>();
 readonly onPitch=computed(()=>this.team().players.filter(p=>p.onPitch&&!p.sentOff&&!p.injured).sort((a,b)=>(a.slot??0)-(b.slot??0)));
 readonly outgoing=computed(()=>this.team().players.filter(p=>p.entered&&!p.withdrawn&&!p.sentOff&&(p.onPitch||p.injured)));
 readonly bench=computed(()=>this.team().players.filter(p=>!p.entered&&!p.injured&&!p.sentOff));
 readonly plan=signal<TacticalPlan>(defaultTacticalPlan());private roster='';
 exchange(event:{first:number;second:number}):void{if(this.busy()||this.locked())return;const firstField=this.outgoing().some(p=>p.id===event.first),secondField=this.outgoing().some(p=>p.id===event.second);if(firstField&&secondField)this.command.emit({type:'SWAP_POSITIONS',playerOut:event.first,playerIn:event.second});else if(firstField!==secondField)this.command.emit({type:'SUBSTITUTE',playerOut:firstField?event.first:event.second,playerIn:firstField?event.second:event.first});}
 resetShape():void{this.plan.update(p=>({...p,positions:{}}));}
 formation='4-3-3';mentality='BALANCED';playerOut=0;playerIn=0;keeper=0;penaltyTaker=0;private teamId=0;
 constructor(){effect(()=>{const team=this.team();if(this.teamId!==team.id){this.teamId=team.id;this.plan.set(defaultTacticalPlan(team.tacticalPlan));this.formation=team.formation;this.mentality=team.mentality;this.penaltyTaker=team.penaltyTaker||0;}const roster=this.onPitch().map(p=>p.id).join(',');if(this.roster!==roster){this.roster=roster;this.plan.set(defaultTacticalPlan(team.tacticalPlan));const positions:Record<number,{x:number;y:number}>={};for(const p of this.onPitch()){const point=this.plan().positions[p.id]||team.tacticalPlan?.positions[p.id];if(point)positions[p.id]=point;}this.plan.update(p=>({...p,positions}));}if(!this.outgoing().some(p=>p.id===+this.playerOut))this.playerOut=0;if(!this.bench().some(p=>p.id===+this.playerIn))this.playerIn=0;});}
}
