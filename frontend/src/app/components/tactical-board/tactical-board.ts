import {playingAbility,preferredPositions} from '../../utils/player-positions';
import { PositioningBoard } from '../positioning-board/positioning-board';
import { defaultTacticalPlan, TacticalPlan } from '../../utils/tactical-plan';
import {available} from '../../utils/player-attributes';
import { Component, computed, input, output, signal } from '@angular/core';
import { ManagedPlayer } from '../../services/management.service';
import { formations, roles } from '../../utils/formations';

@Component({ selector:'app-tactical-board',imports:[PositioningBoard],templateUrl:'./tactical-board.html',styleUrl:'./tactical-board.css' })
export class TacticalBoard {
  readonly playingAbility=playingAbility;readonly preferredPositions=preferredPositions;readonly available=available;readonly plan=input<TacticalPlan>(defaultTacticalPlan());readonly planChange=output<TacticalPlan>();
  readonly starters=computed(()=>this.lineup().map(id=>this.players().find(p=>p.id===id)).filter(p=>!!p).map((p,i)=>({...p,assignedPosition:roles(this.formation())[i],naturalPosition:p.position})));
  selectShirt(id:number):void{const index=this.lineup().indexOf(id);if(index>=0)this.active.set(index);}

  readonly players=input.required<ManagedPlayer[]>(); readonly lineup=input.required<number[]>();
  readonly formation=input.required<string>(); readonly locked=input(false);
  readonly change=output<{index:number;playerId: number}>(); readonly active=signal(0);
  readonly roleList=computed(()=>roles(this.formation()));
  readonly options=computed(()=>[...this.players()].sort((a,b)=>playingAbility(b,this.roleList()[this.active()])-playingAbility(a,this.roleList()[this.active()])));
  used(id:number): boolean { return this.lineup().includes(id)&&this.lineup()[this.active()]!==id; }
}
