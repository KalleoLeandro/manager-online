import {Component,input,output} from '@angular/core';
import {MatchTeam} from '../../services/matchday.service';
import {CoachAction} from '../match-coach-controls/match-coach-controls';
@Component({selector:'app-half-time-talk',templateUrl:'./half-time-talk.html',styleUrl:'./half-time-talk.css'})
export class HalfTimeTalk {
 readonly team=input.required<MatchTeam>();readonly period=input.required<number>();readonly locked=input(false);readonly command=output<CoachAction>();
}
