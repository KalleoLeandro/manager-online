import {available} from '../../utils/player-attributes';
import { Component, computed, input, output, signal } from '@angular/core';
import { ManagedPlayer } from '../../services/management.service';
import { formations, roles } from '../../utils/formations';

@Component({ selector:'app-tactical-board',templateUrl:'./tactical-board.html',styleUrl:'./tactical-board.css' })
export class TacticalBoard {
  readonly available=available;
  readonly players=input.required<ManagedPlayer[]>(); readonly lineup=input.required<number[]>();
  readonly formation=input.required<string>(); readonly locked=input(false);
  readonly change=output<{index:number;playerId: number}>(); readonly active=signal(0);
  readonly roleList=computed(()=>roles(this.formation()));
  readonly slots=computed(()=>{
    const rows=formations[this.formation()]; let index=1;
    const slots=[{index:0,x:50,y:88,role:'GOL'}];
    rows.forEach((count,row)=>{for(let column=0;column<count;column++)slots.push({index:index++,x:100*(column+1)/(count+1),y:67-row*23,role:['DEF','MEI','ATA'][row]});});
    return slots.map(slot=>({...slot,player:this.players().find(p=>p.id===this.lineup()[slot.index])}));
  });
  readonly options=computed(()=>this.players().filter(p=>p.position===this.roleList()[this.active()]));
  used(id:number): boolean { return this.lineup().includes(id)&&this.lineup()[this.active()]!==id; }
}
