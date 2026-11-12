import { Component, computed, effect, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { PlayerDetails, PlayerProfile } from '../player-details/player-details';
import { MatchTeam } from '../../services/matchday.service';
import { MatchPlayerProfilesService } from '../../services/match-player-profiles.service';
import { matchPlayerProfile } from '../../utils/match-player-profile';

@Component({selector:'app-match-player-details',imports:[PlayerDetails],templateUrl:'./match-player-details.html',styleUrl:'./match-player-details.css'})
export class MatchPlayerDetails {
 readonly leagueId=input.required<string>();readonly team=input.required<MatchTeam>();readonly playerId=input.required<number>();readonly closed=output<void>();
 private readonly profiles=inject(MatchPlayerProfilesService);private readonly teamId=computed(()=>this.team().id);
 private readonly data=signal<PlayerProfile[]>([]);readonly error=signal('');private readonly retry=signal(0);
 readonly live=computed(()=>this.team().players.find(p=>p.id===this.playerId())||null);
 readonly squad=computed(()=>this.data().map(base=>{const live=this.team().players.find(p=>p.id===base.id);return live?matchPlayerProfile(base,live):base;}));
 readonly profile=computed(()=>this.squad().find(p=>p.id===this.playerId())||null);
 private readonly status=viewChild<ElementRef<HTMLDialogElement>>('status');
 constructor(){
  effect(cleanup=>{const id=this.leagueId(),teamId=this.teamId();this.retry();this.error.set('');const request=this.profiles.squad(id,teamId).subscribe({next:players=>{this.data.set(players);if(!players.some(p=>p.id===this.playerId()))this.error.set('Ficha do jogador não encontrada nesta liga.');},error:e=>this.error.set(e.error?.message||'Não foi possível carregar a ficha do jogador.')});cleanup(()=>request.unsubscribe());});
  effect(()=>{const dialog=this.status()?.nativeElement;if(dialog&&!dialog.open)dialog.showModal();});
 }
 reload():void{this.retry.update(n=>n+1);}
 cancel(event:Event):void{event.preventDefault();this.closed.emit();}
}
