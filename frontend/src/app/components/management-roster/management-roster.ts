import {available} from '../../utils/player-attributes';
import { Component, computed, input, output, signal } from '@angular/core';
import { ManagedPlayer } from '../../services/management.service';
import { PlayerPortrait } from '../player-portrait/player-portrait';
import { PlayerDetails } from '../player-details/player-details';
@Component({selector:'app-management-roster',imports:[PlayerPortrait,PlayerDetails],templateUrl:'./management-roster.html',styleUrl:'./management-roster.css'})
export class ManagementRoster {
  readonly available=available;
  readonly players=input.required<ManagedPlayer[]>(); readonly lineup=input.required<number[]>(); readonly locked=input(false);
  readonly selectPlayer=output<number>(); readonly query=signal(''); readonly position=signal('');
  readonly teamName=input(''); readonly profileId=signal<number|null>(null);
  readonly profile=computed(()=>this.players().find(p=>p.id===this.profileId())||null);
  readonly visible=computed(()=>this.players().filter(p=>(!this.position()||p.position===this.position())&&p.name.toLowerCase().includes(this.query().toLowerCase())));
}
