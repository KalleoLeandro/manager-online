import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';import { FormsModule } from '@angular/forms';import { takeUntilDestroyed } from '@angular/core/rxjs-interop';import { Subscription } from 'rxjs';import { NationalChampionship, NationalChampionshipService } from '../../services/national-championship.service';import { ClubCrest } from '../../components/club-crest/club-crest';import { dateLabel } from '../../utils/game-calendar';
@Component({selector:'app-national-championship',imports:[FormsModule,ClubCrest],templateUrl:'./national-championship.html',styleUrl:'./national-championship.css'})
export class NationalChampionshipPage {
 readonly leagueId=input.required<string>();private readonly api=inject(NationalChampionshipService);private readonly destroy=inject(DestroyRef);
 readonly gameRound=input.required<number>();private request?:Subscription;
 readonly data=signal<NationalChampionship|null>(null);readonly loading=signal(true);readonly error=signal('');readonly onlyMine=signal(true);readonly round=signal(0);readonly label=dateLabel;
 readonly rounds=computed(()=>Array.from({length:this.data()?.season.rounds||0},(_,i)=>i+1));
 readonly visible=computed(()=>{const data=this.data();return(data?.fixtures||[]).filter(f=>(!this.onlyMine()||f.homeTeamId===data?.ownTeamId||f.awayTeamId===data?.ownTeamId)&&(!this.round()||f.round===this.round()));});
 constructor(){effect(()=>{this.gameRound();this.leagueId();untracked(()=>this.load());});}
 load(year?:number):void{this.request?.unsubscribe();this.loading.set(true);this.error.set('');this.request=this.api.load(this.leagueId(),year).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:data=>{this.data.set(data);this.round.set(0);this.loading.set(false);},error:e=>{this.error.set(e.error?.message||'Não foi possível carregar o campeonato.');this.loading.set(false);}});}
}
