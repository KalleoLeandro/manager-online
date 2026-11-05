import { Component, DestroyRef, computed, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { auditTime, finalize, interval, Subject } from 'rxjs';
import { Matchday, MatchdayService } from '../../services/matchday.service';
import { SaveService } from '../../services/save.service';
import { SaveRealtimeService } from '../../services/save-realtime.service';
import { MatchCoachControls, CoachAction } from '../../components/match-coach-controls/match-coach-controls';
import { ClubCrest } from '../../components/club-crest/club-crest';
import { GameIcon } from '../../components/game-icon/game-icon';
import { LiveStatus } from '../../components/live-status/live-status';
import { LocalPlayers } from '../../components/local-players/local-players';
import { currentPath } from '../../utils/navigation';
import { matchClock } from '../../utils/match-clock';

@Component({selector:'app-match-tactics',providers:[SaveRealtimeService],imports:[MatchCoachControls,ClubCrest,GameIcon,LiveStatus,LocalPlayers],templateUrl:'./match-tactics.html',styleUrl:'./match-tactics.css'})
export class MatchTacticsPage implements OnInit {
 private readonly api=inject(MatchdayService);private readonly saves=inject(SaveService);private readonly destroy=inject(DestroyRef);
 readonly realtime=inject(SaveRealtimeService);readonly id=currentPath().split('/')[1]||'';
 private readonly fixture=Number(currentPath().split('/')[2])||undefined;
 readonly data=signal<Matchday|null>(null);readonly busy=signal(false);readonly loading=signal(true);readonly opened=signal(false);readonly error=signal('');readonly notice=signal('');readonly clock=matchClock;
 readonly match=computed(()=>this.data()?.selected);readonly own=computed(()=>{const m=this.match(),id=this.data()?.ownTeamId;if(!m)return null;return m.home.id===id?m.home:m.away.id===id?m.away:null;});
 readonly editable=computed(()=>this.opened()&&this.match()?.status==='PAUSED'&&!!this.match()?.canResume);
 private refreshes=new Subject<void>();private fetching=false;private again=false;
 ngOnInit():void{const access=this.saves.access(this.id);if(!access){this.error.set('Entre nesta liga para ajustar seu time.');this.loading.set(false);return;}this.realtime.connect({saveId:this.id,accessToken:access.accessToken,activity:'MANAGING_TEAM'});this.refreshes.pipe(auditTime(150),takeUntilDestroyed(this.destroy)).subscribe(()=>this.load());this.realtime.gameEvents$.pipe(takeUntilDestroyed(this.destroy)).subscribe(e=>{if(e.event==='MATCH_UPDATED')this.refreshes.next();});interval(5000).pipe(takeUntilDestroyed(this.destroy)).subscribe(()=>this.refreshes.next());this.load();}
 load():void{if(this.fetching){this.again=true;return;}this.fetching=true;this.api.load(this.id,this.fixture).pipe(finalize(()=>{this.fetching=false;this.loading.set(false);if(this.again){this.again=false;this.load();}}),takeUntilDestroyed(this.destroy)).subscribe({next:data=>{const current=this.match();if(current&&data.selected?.fixtureId===current.fixtureId&&data.selected.revision<current.revision)return;this.data.set(data);if(!this.opened()&&!this.busy()&&this.own()&&data.selected?.status!=='FINISHED'&&!data.selected?.varReview&&!(data.selected?.goalCelebrationRemainingMillis)){this.open();}},error:e=>this.error.set(e.error?.message||'Não foi possível carregar as táticas.')});}
 private open():void{const m=this.match();if(!m)return;this.busy.set(true);this.api.action(this.id,m.fixtureId,{type:'OPEN_TACTICS',revision:m.revision}).pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:data=>{this.data.set(data);this.opened.set(true);this.error.set('');},error:e=>{this.error.set(e.error?.message||'Não foi possível pausar para os ajustes.');}});}
 command(action:CoachAction):void{const m=this.match();if(!m||this.busy()||!this.editable())return;this.busy.set(true);this.error.set('');this.notice.set('');this.api.action(this.id,m.fixtureId,{...action,revision:m.revision}).pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:data=>{this.data.set(data);this.notice.set('Ajuste aplicado. A partida continua pausada até você concluir.');},error:e=>{this.error.set(e.error?.message||'Não foi possível aplicar o ajuste.');this.refreshes.next();}});}
 finish():void{const m=this.match();if(this.busy())return;if(!m||m.status==='FINISHED'||!this.opened()){location.hash=`match/${this.id}`;return;}if(!m.canResume){location.hash=`match/${this.id}`;return;}this.busy.set(true);this.error.set('');this.api.action(this.id,m.fixtureId,{type:'RESUME',revision:m.revision}).pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:()=>location.hash=`match/${this.id}/${m.fixtureId}`,error:e=>{this.error.set(e.error?.message||'Não foi possível concluir os ajustes.');this.refreshes.next();}});}
}
