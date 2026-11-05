import {Component,DestroyRef,ElementRef,computed,effect,inject,input,signal,viewChild} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {interval,finalize} from 'rxjs';
import {TournamentDrawService,TournamentDraw as Draw} from '../../services/tournament-draw.service';
import {SaveRealtimeService} from '../../services/save-realtime.service';
import {ClubCrest} from '../club-crest/club-crest';

@Component({selector:'app-tournament-draw',imports:[ClubCrest],templateUrl:'./tournament-draw.html',styleUrl:'./tournament-draw.css'})
export class TournamentDraw {
 readonly leagueId=input.required<string>();
 private readonly api=inject(TournamentDrawService);private readonly destroy=inject(DestroyRef);
 private readonly realtime=inject(SaveRealtimeService,{optional:true});
 private readonly dialog=viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
 private readonly table=viewChild<ElementRef<HTMLElement>>('table');
 readonly active=signal<Draw|null>(null);readonly now=signal(Date.now());readonly revealAll=signal(false);readonly busy=signal(false);readonly error=signal('');private fetching=false;
 readonly count=computed(()=>{const draw=this.active();return draw?this.revealAll()?draw.rows.length:Math.min(draw.rows.length,Math.max(0,Math.floor((this.now()-draw.startedAt)/600)+1)):0;});
 readonly rows=computed(()=>this.active()?.rows.slice(0,this.count())||[]);
 constructor(){
  effect(()=>{this.leagueId();this.load();});
  effect(()=>{const active=this.active();queueMicrotask(()=>{if(this.destroy.destroyed)return;const modal=this.dialog().nativeElement;if(active&&!modal.open)modal.showModal();else if(!active&&modal.open)modal.close();});});
  effect(()=>{this.count();queueMicrotask(()=>{if(this.destroy.destroyed)return;const table=this.table()?.nativeElement;if(table)table.scrollTop=table.scrollHeight;});});
  interval(100).pipe(takeUntilDestroyed(this.destroy)).subscribe(()=>this.now.set(Date.now()));
  interval(5000).pipe(takeUntilDestroyed(this.destroy)).subscribe(()=>this.load());
  this.realtime?.gameEvents$.pipe(takeUntilDestroyed(this.destroy)).subscribe(e=>{if(e.event==='DRAW_CREATED')this.load();});
 }
 load():void{if(this.fetching||this.active()||!this.leagueId())return;this.fetching=true;this.api.pending(this.leagueId()).pipe(finalize(()=>this.fetching=false),takeUntilDestroyed(this.destroy)).subscribe({next:draws=>{if(draws[0]){this.revealAll.set(false);this.active.set(draws[0]);}},error:()=>{/* The next connection/poll retries without discarding the pending draw. */}});}
 close():void{const draw=this.active();if(!draw||this.busy())return;this.busy.set(true);this.api.acknowledge(this.leagueId(),draw.id).pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.active.set(null);this.load();},error:()=>this.error.set('Não foi possível confirmar a visualização. Tente novamente.')});}
}
