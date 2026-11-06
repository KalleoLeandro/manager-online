import { finalize, forkJoin } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatchdayService, MatchTicket } from '../../services/matchday.service';
import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { ClubCrest } from '../club-crest/club-crest';
import { GameIcon } from '../game-icon/game-icon';
import { DayProgressService, DayProgress } from '../../services/day-progress.service';
import { Room } from '../../services/save.service';

@Component({selector:'app-matchday-entry',imports:[ClubCrest,GameIcon],templateUrl:'./matchday-entry.html',styleUrl:'./matchday-entry.css'})
export class MatchdayEntry {
 readonly leagueId=input.required<string>();readonly teamId=input.required<number>();readonly teamName=input.required<string>();
 readonly leagueName=input.required<string>();readonly coach=input.required<string>();readonly room=input.required<Room>();
 private readonly calendar=inject(DayProgressService);private readonly matches=inject(MatchdayService);readonly playing=signal(false);
 private readonly destroy=inject(DestroyRef);readonly busy=signal(false);readonly error=signal('');readonly disabled=input(false);
 readonly ticket=signal<MatchTicket|null>(null);
 readonly revision=input(0);readonly progress=signal<DayProgress|null>(null);
 readonly available=computed(()=>{const p=this.progress();return this.playing()||!!p?.nextMatch&&p.currentDate===p.nextMatch;});
 readonly hint=computed(()=>this.playing()?'Continuar partida':this.available()?'Entrar em campo':this.progress()?.nextMatch?'Disponível no dia '+this.progress()!.nextMatch!.split('-').reverse().join('/'):'Sem partida agendada');
 constructor(){effect(cleanup=>{this.revision();this.room();const id=this.leagueId();this.progress.set(null);this.playing.set(false);const request=forkJoin({progress:this.calendar.state(id),match:this.matches.load(id)}).subscribe({next:({progress,match})=>{this.progress.set(progress);this.ticket.set(match.ticket||null);this.playing.set(match.matches.some(m=>(m.homeId===this.teamId()||m.awayId===this.teamId())&&m.status!=='FINISHED'));},error:()=>this.progress.set(null)});cleanup(()=>request.unsubscribe());});}
 enter():void{
  if(!this.available()||this.busy()||this.disabled())return;
  if(this.playing()){location.hash='match/'+this.leagueId();return;}
  this.error.set('');this.busy.set(true);
  this.matches.start(this.leagueId()).pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({
   next:()=>{location.hash='match/'+this.leagueId();},
   error:e=>this.error.set(e.error?.message||'Não foi possível iniciar a partida. Tente novamente.')
  });
 }
 readonly readyCount=input.required<number>();
}
