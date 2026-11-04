import { Component, computed, input, signal } from '@angular/core';
import { Player } from '../../services/catalog.service';
import { PlayerPortrait } from '../player-portrait/player-portrait';
import { PlayerDetails } from '../player-details/player-details';

@Component({ selector: 'app-team-roster', imports: [PlayerPortrait,PlayerDetails], templateUrl: './team-roster.html', styleUrl: './team-roster.css' })
export class TeamRoster {
  readonly players=input.required<Player[]>(); readonly teamName=input(''); readonly profileId=signal<number|null>(null);
  readonly profile=computed(()=>this.players().find(p=>p.id===this.profileId())||null);
}
