import { Component, input } from '@angular/core';
import { ClubCrest } from '../club-crest/club-crest';
import { GameIcon } from '../game-icon/game-icon';
import { Room } from '../../services/save.service';

@Component({selector:'app-matchday-entry',imports:[ClubCrest,GameIcon],templateUrl:'./matchday-entry.html',styleUrl:'./matchday-entry.css'})
export class MatchdayEntry {
 readonly leagueId=input.required<string>();readonly teamId=input.required<number>();readonly teamName=input.required<string>();
 readonly leagueName=input.required<string>();readonly coach=input.required<string>();readonly room=input.required<Room>();
 readonly readyCount=input.required<number>();
}
