import { AfterViewInit, Component, ElementRef, effect, input, output, viewChild } from '@angular/core';
import { PlayerPortrait } from '../player-portrait/player-portrait';
import { ClubCrest } from '../club-crest/club-crest';
import { MatchIncident as Incident } from '../../utils/match-incidents';
import { matchClock } from '../../utils/match-clock';

@Component({selector:'app-match-incident',imports:[PlayerPortrait,ClubCrest],templateUrl:'./match-incident.html',styleUrl:'./match-incident.css'})
export class MatchIncident implements AfterViewInit {
 readonly incident=input.required<Incident>();readonly completed=output<string>();readonly clock=matchClock;
 private readonly dialog=viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
 constructor(){effect(cleanup=>{const key=this.incident().key;const timer=setTimeout(()=>this.completed.emit(key),5000);cleanup(()=>clearTimeout(timer));});}
 ngAfterViewInit():void{this.dialog().nativeElement.showModal();}
 close():void{this.completed.emit(this.incident().key);}
}
