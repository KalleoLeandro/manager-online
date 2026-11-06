import {ClubCrest} from '../club-crest/club-crest';
import { Component, computed, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Access } from '../../services/save.service';

@Component({selector:'app-league-card',imports:[ClubCrest,DatePipe],templateUrl:'./league-card.html',styleUrl:'./league-card.css'})
export class LeagueCard {
  readonly access=input.required<Access>();
  readonly open=output<Access>();readonly remove=output<Access>();
  readonly me=computed(()=>this.access().room.members.find(m=>m.id===this.access().memberId));
}
