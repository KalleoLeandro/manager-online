import { AfterViewInit, Component, ElementRef, effect, input, output, signal, viewChild } from '@angular/core';
import { PlayerPortrait } from '../player-portrait/player-portrait';
import { ClubCrest } from '../club-crest/club-crest';
import { GoalCelebration as GoalDetails } from '../../utils/goal-celebration';
import { matchClock } from '../../utils/match-clock';

@Component({selector:'app-goal-celebration',imports:[PlayerPortrait,ClubCrest],templateUrl:'./goal-celebration.html',styleUrl:'./goal-celebration.css'})
export class GoalCelebration implements AfterViewInit {
 readonly goal=input.required<GoalDetails>();readonly completed=output<string>();readonly clock=matchClock;
 readonly duration=signal(5000);private goalKey='';
 private readonly dialog=viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
 constructor(){effect(onCleanup=>{const goal=this.goal();if(goal.key!==this.goalKey){this.goalKey=goal.key;this.duration.set(goal.remainingMillis);}const timer=setTimeout(()=>this.completed.emit(goal.key),Math.max(0,goal.remainingMillis));onCleanup(()=>clearTimeout(timer));});}
 ngAfterViewInit():void{this.dialog().nativeElement.showModal();}
}
