import { DayProgressService } from '../../services/day-progress.service';
import {LeagueExit} from '../../components/league-exit/league-exit';
import {narrationRoster,narrationParts} from '../../utils/match-narration';
import { MatchOptions } from '../../components/match-options/match-options';
import { MatchTouchline } from '../../components/match-touchline/match-touchline';
import { TouchlineCommand } from '../../services/matchday.service';
import { MatchPanel } from '../../components/match-panel/match-panel';
import { MatchPanelLayout, MatchPanelId } from '../../utils/match-panel-layout';
import { LocalPlayers } from '../../components/local-players/local-players';
import { Component, DestroyRef, OnInit, computed, effect, untracked, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, auditTime, interval, finalize } from 'rxjs';
import { MatchdayService, Matchday, MatchAction, MatchPlayer } from '../../services/matchday.service';
import { SaveService } from '../../services/save.service';
import { SaveRealtimeService } from '../../services/save-realtime.service';
import { ClubCrest } from '../../components/club-crest/club-crest';
import { LiveStatus } from '../../components/live-status/live-status';
import { MatchStatistics } from '../../components/match-statistics/match-statistics';
import { currentPath } from '../../utils/navigation';
import { matchClock, matchIsHalfTime, scoreboardClock, announcedStoppageTime } from '../../utils/match-clock';
import { dateLabel } from '../../utils/game-calendar';
import { GoalCelebration } from '../../components/goal-celebration/goal-celebration';
import { currentGoal } from '../../utils/goal-celebration';
import { GameIcon } from '../../components/game-icon/game-icon';
import { MatchAudioService } from '../../services/match-audio.service';
import { VarReview } from '../../components/var-review/var-review';

@Component({selector:'app-matchday',providers:[SaveRealtimeService],imports:[LeagueExit,MatchOptions,MatchTouchline,MatchPanel,LocalPlayers,VarReview,GameIcon,GoalCelebration,FormsModule,ClubCrest,LiveStatus,MatchStatistics],templateUrl:'./matchday.html',styleUrl:'./matchday.css'})
export class MatchdayPage implements OnInit {
 private readonly api=inject(MatchdayService);private readonly saves=inject(SaveService);private readonly destroy=inject(DestroyRef);
 readonly localRoom=computed(()=>this.realtime.room()||this.saves.access(this.id)?.room||null);
 readonly hasLocalControl=computed(()=>!!this.saves.localOwner(this.id));
 readonly realtime=inject(SaveRealtimeService);readonly id=currentPath().split('/')[1]||'';
 readonly layout=new MatchPanelLayout();
 readonly rightTab=signal<'home'|'away'|'stats'|'scores'>('home');
 readonly eventFilter=signal('ALL');
 togglePanel(id:MatchPanelId):void{this.layout.toggle(id);if(this.layout.visible(id)){if(id==='stats'||id==='scores')this.rightTab.set(id);if(id==='lineups'&&['stats','scores'].includes(this.rightTab()))this.rightTab.set('home');}}
 setRightTab(tab:'home'|'away'|'stats'|'scores'):void{this.rightTab.set(tab);this.layout.reveal(tab==='stats'?'stats':tab==='scores'?'scores':'lineups');}
 readonly network=computed(()=>{const room=this.realtime.room()||this.localRoom();return this.data()?.localMultiplayer===true?false:room?room.capacity>1&&!room.localMultiplayer:true;});
 readonly decisionRequired=computed(()=>!!this.own()&&!!this.match()?.canResume&&!!this.match()?.awaitingCoachDecision);
 private readonly calendar=inject(DayProgressService);readonly gameDay=signal(false);
 readonly speedBusy=signal(false);
 readonly data=signal<Matchday|null>(null);readonly error=signal('');readonly busy=signal(false);readonly loading=signal(false);
 readonly selected=signal<number|undefined>(/^\d+$/.test(currentPath().split('/')[2]||'')?Number(currentPath().split('/')[2]):undefined);readonly importantOnly=signal(false);readonly notice=signal('');
 readonly match=computed(()=>this.data()?.selected);readonly own=computed(()=>{const m=this.match(),id=this.data()?.ownTeamId;if(!m)return null;return m.home.id===id?m.home:m.away.id===id?m.away:null;});
 readonly halfTime=computed(()=>matchIsHalfTime(this.match()));
 readonly scoreClock=computed(()=>scoreboardClock(this.match()));
 readonly announcedAddedTime=computed(()=>announcedStoppageTime(this.match()));
 private readonly completedGoal=signal('');
 readonly goal=computed(()=>{const goal=currentGoal(this.match());return goal?.key===this.completedGoal()?null:goal;});
 finishGoal(key:string):void{this.completedGoal.set(key);this.refreshes.next();}
 refreshReview():void{this.refreshes.next();}
 private readonly narrationRoster=computed(()=>{const m=this.match();return m?narrationRoster(m.home,m.away):null;});
 readonly events=computed(()=>{const list=this.match()?.events||[],goal=this.goal(),review=this.match()?.varReview;return [...list].reverse().filter(e=>(!review||e.sequence<=review.goalSequence+1||e.type==='VAR_DECISION')&&(!goal||e.sequence<=goal.sequence)&&this.acceptEvent(e.type)&&(!this.importantOnly()||!['PASS_SEQUENCE','INTERCEPTION','THROW_IN','FOUL','SHOT_OFF','OFFSIDE','SAVE'].includes(e.type))).map(event=>({...event,parts:this.narrationRoster()?narrationParts(event,this.narrationRoster()!):[{text:event.text}]}));});
 readonly clock=matchClock;readonly dateLabel=dateLabel;
 private refreshes=new Subject<void>();private fetching=false;private again=false;
 private readonly audio=inject(MatchAudioService);
 constructor(){effect(()=>{const match=this.match();untracked(()=>this.audio.observe(match));});this.destroy.onDestroy(()=>this.audio.stopMatch());}
 ngOnInit():void {
  const access=this.saves.access(this.id);if(!access){this.error.set('Entre nesta liga para acompanhar a partida.');return;}
  this.realtime.connect({saveId:this.id,accessToken:access.accessToken,activity:'MANAGING_TEAM'});
  this.refreshes.pipe(auditTime(150),takeUntilDestroyed(this.destroy)).subscribe(()=>this.load());
  this.realtime.gameEvents$.pipe(takeUntilDestroyed(this.destroy)).subscribe(e=>{if(e.event==='MATCH_UPDATED'){if((e.payload as {fixtureId:number}).fixtureId===0)this.selected.set(undefined);this.refreshes.next();}});
  interval(5000).pipe(takeUntilDestroyed(this.destroy)).subscribe(()=>this.refreshes.next());
  this.calendar.state(this.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:p=>this.gameDay.set(!!p.nextMatch&&p.currentDate===p.nextMatch),error:e=>this.error.set(e.error?.message||'Não foi possível verificar a data da partida.')});
  this.load();
 }
 load():void {
  if(this.fetching){this.again=true;return;}this.fetching=true;this.loading.set(!this.data());const requested=this.selected();
  this.api.load(this.id,requested).pipe(finalize(()=>{this.fetching=false;this.loading.set(false);if(this.again){this.again=false;this.load();}}),takeUntilDestroyed(this.destroy)).subscribe({next:d=>{
   if(requested!==this.selected()){this.again=true;return;}
   const current=this.match();if(current&&d.selected?.fixtureId===current.fixtureId&&d.selected.revision<current.revision)return;
   if(!current&&d.selected)this.rightTab.set(d.selected.away.id===d.ownTeamId?'away':'home');
   if(!current&&requested===undefined&&d.selected?.status==='FINISHED')d={...d,selected:null};
   this.data.set(d);
   const ownPending=d.matches.find(game=>(game.homeId===d.ownTeamId||game.awayId===d.ownTeamId)&&game.status==='PAUSED');
   if(ownPending&&d.selected?.fixtureId!==ownPending.fixtureId){this.selected.set(ownPending.fixtureId);this.again=true;return;}
   if(d.selected?.status==='PAUSED'&&(d.selected.canResume||d.selected.awaitingCoachDecision&&d.selected.pendingCoachTeams.includes(d.ownTeamId))&&!d.selected.varReview&&d.selected.goalCelebrationRemainingMillis<=0){location.hash=`match-tactics/${this.id}/${d.selected.fixtureId}`;}

  },error:e=>this.error.set(e.error?.message||'Não foi possível atualizar a partida.')});
 }
 changeSpeed(percent:number):void{if(this.speedBusy()||!(this.data()?.canChangeSpeed||this.data()?.localMultiplayer&&this.saves.localOwner(this.id)))return;this.speedBusy.set(true);this.error.set('');const selected=this.match()?.fixtureId;this.api.speed(this.id,percent,selected).pipe(finalize(()=>this.speedBusy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:data=>{if(this.match()?.fixtureId===selected)this.data.set(data);else this.refreshes.next();},error:e=>{this.error.set(e.error?.message||'Não foi possível alterar a velocidade.');this.refreshes.next();}});}
 select(fixture:number):void{this.selected.set(fixture);this.error.set('');this.load();}
 start():void{if(this.busy()||!this.gameDay())return;this.busy.set(true);this.api.start(this.id).pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:d=>this.data.set(d),error:e=>this.error.set(e.error?.message||'Não foi possível iniciar a jornada.')});}
 openTactics():void{const m=this.match();if(!m||!this.own()||this.busy())return;this.error.set('');this.busy.set(true);this.api.action(this.id,m.fixtureId,{type:'OPEN_TACTICS',revision:m.revision}).pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:()=>location.hash=`match-tactics/${this.id}/${m.fixtureId}`,error:e=>{this.error.set(e.error?.message||'Não foi possível abrir os ajustes.');this.refreshes.next();}});}
 command(type:'RESUME'|TouchlineCommand):void{const m=this.match();if(!m||this.busy())return;this.error.set('');this.busy.set(true);this.api.action(this.id,m.fixtureId,{type,revision:m.revision}).pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:d=>{this.data.set(d);this.refreshes.next();},error:e=>{this.error.set(e.error?.message||'Não foi possível confirmar os ajustes.');this.refreshes.next();}});}
 acceptEvent(type:string):boolean{const filter=this.eventFilter();return filter==='ALL'||filter==='GOALS'&&['GOAL','GOAL_DISALLOWED','VAR_REVIEW','VAR_DECISION','GOAL_PENDING'].includes(type)||filter==='CARDS'&&['YELLOW','RED','REFEREE_NOTICE','COACH_WARNING','COACH_RED'].includes(type)||filter==='SUBS'&&type==='SUBSTITUTION';}
 players(side:'home'|'away'):MatchPlayer[]{return this.match()?.[side].players.slice().sort((a,b)=>Number(b.onPitch)-Number(a.onPitch)||Number(b.entered)-Number(a.entered))||[];}
}
