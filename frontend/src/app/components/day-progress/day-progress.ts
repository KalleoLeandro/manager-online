import { Component, DestroyRef, computed, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { DayProgressService, DayProgress as Progress } from '../../services/day-progress.service';
import { Room } from '../../services/save.service';
import { GameIcon } from '../game-icon/game-icon';
import { dateLabel } from '../../utils/game-calendar';
@Component({selector:'app-day-progress',imports:[GameIcon],templateUrl:'./day-progress.html',styleUrl:'./day-progress.css'})
export class DayProgress {
 readonly leagueId=input.required<string>();readonly room=input.required<Room>();readonly revision=input(0);readonly disabled=input(false);readonly ready=input(false);readonly advanced=output<void>();
 private readonly api=inject(DayProgressService);private readonly destroy=inject(DestroyRef);
 readonly data=signal<Progress|null>(null);readonly busy=signal(false);readonly error=signal('');readonly notice=signal('');readonly expanded=signal(false);readonly label=dateLabel;
 readonly matchDay=computed(()=>!!this.data()?.nextMatch&&this.data()!.currentDate>=this.data()!.nextMatch!);
 readonly blocked=computed(()=>this.disabled()||this.ready()||this.busy()||this.room().status==='LIVE'||this.matchDay()||!this.data());
 readonly events=computed(()=>[...(this.data()?.events||[])].reverse().slice(0,12));
 constructor(){effect(cleanup=>{const id=this.leagueId();this.revision();this.room().roundNumber;const request=this.api.state(id).subscribe({next:d=>this.data.set(d),error:()=>this.error.set('Não foi possível carregar o avanço do calendário.')});cleanup(()=>request.unsubscribe());});}
 toggleEvents():void{this.expanded.update(v=>!v);}
 advance(days:number):void{const d=this.data();if(!d||this.blocked())return;this.busy.set(true);this.error.set('');this.api.advance(this.leagueId(),d.currentDate,days).pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:result=>{this.data.set(result);this.notice.set(result.stopReason);if(result.stopReason.includes('lesão'))this.expanded.set(true);this.advanced.emit();},error:e=>this.error.set(e.error?.message||'Não foi possível avançar os dias.')});}
}
