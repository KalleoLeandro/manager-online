import {TournamentDraw} from '../../components/tournament-draw/tournament-draw';
import {positionFamily} from '../../utils/player-positions';
import { SeasonBudget } from '../../components/season-budget/season-budget';
import { ClubFinancePage } from '../club-finance/club-finance';
import { TransferMarketPage } from '../transfer-market/transfer-market';
import { DayProgress } from '../../components/day-progress/day-progress';
import { defaultTacticalPlan, TacticalPlan } from '../../utils/tactical-plan';
import { TeamStrategy } from '../../components/team-strategy/team-strategy';
import { LocalPlayers } from '../../components/local-players/local-players';
import { SeasonCalendarPage } from '../season-calendar/season-calendar';
import { NationalChampionshipPage } from '../national-championship/national-championship';
import { MatchdayEntry } from '../../components/matchday-entry/matchday-entry';
import { GameIcon } from '../../components/game-icon/game-icon';
import { ManagementCalendar } from '../../components/management-calendar/management-calendar';
import { ManagerAvatar } from '../../components/manager-avatar/manager-avatar';
import { CoachAnnouncement } from '../../components/coach-announcement/coach-announcement';
import { TeamMessagesPage } from '../team-messages/team-messages';
import { PlayerSearchPage } from '../player-search/player-search';
import { JuniorTeamPage } from '../junior-team/junior-team';
import { ManagementSectionPage } from '../management-section/management-section';
import { ManagementPage, managementPages, managementNavigation } from '../../utils/management-navigation';
import {available,effectiveAbility} from '../../utils/player-attributes';
import { WorldCompetitionsPage } from '../world-competitions/world-competitions';
import { LeagueTeamsPage } from '../league-teams/league-teams';
import { Component, DestroyRef, computed, effect, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Observable, of, switchMap } from 'rxjs';
import { ManagementService, TeamManagement } from '../../services/management.service';
import { SaveService } from '../../services/save.service';
import { SaveRealtimeService } from '../../services/save-realtime.service';
import { AuthService } from '../../services/auth.service';
import { ClubCrest } from '../../components/club-crest/club-crest';
import { LiveStatus } from '../../components/live-status/live-status';
import { TacticalBoard } from '../../components/tactical-board/tactical-board';
import { ManagementRoster } from '../../components/management-roster/management-roster';
import { currentPath } from '../../utils/navigation';
import { clubTheme } from '../../utils/club-theme';
import { formations, roles, suggestLineup } from '../../utils/formations';
import { readLocal, writeLocal } from '../../utils/local-storage';

@Component({selector:'app-team-management',providers:[SaveRealtimeService],imports:[TournamentDraw,SeasonBudget,ClubFinancePage,TransferMarketPage,DayProgress,TeamStrategy,LocalPlayers,SeasonCalendarPage,MatchdayEntry,GameIcon,NationalChampionshipPage,ManagementCalendar,ManagerAvatar,CoachAnnouncement,PlayerSearchPage,TeamMessagesPage,JuniorTeamPage,ManagementSectionPage,LeagueTeamsPage,WorldCompetitionsPage,ClubCrest,FormsModule,CurrencyPipe,DecimalPipe,LiveStatus,TacticalBoard,ManagementRoster],templateUrl:'./team-management.html',styleUrl:'./team-management.css'})
export class TeamManagementPage implements OnInit {
  constructor(){effect(()=>{if(this.realtime.room()?.status==='LIVE')location.hash=`match/${this.id}`;});}
  private readonly api=inject(ManagementService); private readonly saves=inject(SaveService);
  private readonly auth=inject(AuthService); private readonly destroy=inject(DestroyRef);
  readonly realtime=inject(SaveRealtimeService);
  readonly id=currentPath().split('/')[1]||'';
  readonly requestedPlayer=signal(0);
  startNegotiation(id:number):void{this.requestedPlayer.set(id);this.navigate('transfers');}
  readonly budgetCleared=signal(false);readonly data=signal<TeamManagement|null>(null); readonly tab=signal<ManagementPage>('messages');
  readonly tacticalPlan=signal<TacticalPlan>(defaultTacticalPlan());
  changePlan(plan:TacticalPlan):void{if(this.locked())return;this.tacticalPlan.set(plan);this.dirty.set(true);this.notice.set('Posicionamento e estratégia alterados. Salve para confirmar.');}
  readonly dark=signal(false); readonly lineup=signal<number[]>([]); readonly formation=signal('4-3-3'); readonly mentality=signal('BALANCED');
  readonly dirty=signal(false); readonly saving=signal(false); readonly confirming=signal(false);
  readonly error=signal(''); readonly notice=signal(''); readonly conflict=signal(false);
  readonly formationNames=Object.keys(formations);
  readonly tabs=managementNavigation;
  readonly openMenu=signal<ManagementPage|null>(null);
  readonly pageHeading=computed(()=>managementPages[this.tab()]);
  readonly activeMenu=computed(()=>this.tabs.find(item=>item.pages.includes(this.tab())));
  selectMenu(id:ManagementPage):void{const menu=this.tabs.find(item=>item.id===id)!;if(menu.links.length)this.openMenu.update(current=>current===id?null:id);else{this.tab.set(id);this.openMenu.set(null);}}
  navigate(page:ManagementPage):void{this.tab.set(page);this.openMenu.set(null);}

  readonly theme=computed(()=>clubTheme(this.data()?.palette.primary||'#334155',this.data()?.palette.secondary||'#ffffff',this.dark(),this.data()?.palette.awayAccent,this.data()?.palette.homeAccent));
  readonly me=computed(()=>(this.realtime.room()||this.data()?.room)?.members.find(m=>m.id===this.data()?.memberId));
  readonly readyCount=computed(()=>(this.realtime.room()||this.data()?.room)?.members.filter(m=>m.ready).length||0);
  readonly locked=computed(()=>!!this.me()?.ready||this.saving()||this.realtime.deleted());
  readonly strength=computed(()=>{const players=this.lineup().map(id=>this.data()?.players.find(p=>p.id===id)).filter(p=>!!p);return players.length?Math.round(players.reduce((sum,p)=>sum+effectiveAbility(p),0)/players.length):0;});
  readonly fitness=computed(()=>{const players=this.data()?.players||[];return players.length?Math.round(players.reduce((sum,p)=>sum+p.fitness,0)/players.length):0;});
  readonly valid=computed(()=>this.lineup().length===11&&new Set(this.lineup()).size===11&&this.lineup().every((id,index)=>this.data()?.players.some(p=>p.id===id&&available(p))));
  readonly showIntroduction=signal(false);
  readonly introductionSaving=signal(false);readonly introductionError=signal('');
  readonly managerPhotoUrl=this.auth.session()?.photoUrl;
  readonly managerFirstName=this.auth.session()?.firstName;
  readonly managerLastName=this.auth.session()?.lastName;
  dismissIntroduction():void{if(this.introductionSaving())return;this.introductionSaving.set(true);this.introductionError.set('');this.api.acknowledgeIntroduction(this.id).pipe(finalize(()=>this.introductionSaving.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:()=>this.showIntroduction.set(false),error:()=>this.introductionError.set('Não foi possível registrar a leitura. Tente novamente.')});}
  private connected=false;
  private themeKey(teamId: number) {return `club-theme:${this.auth.session()?.id||'guest'}:${teamId}`;}
  ngOnInit(): void {
    this.load();
    this.realtime.gameEvents$.pipe(takeUntilDestroyed(this.destroy)).subscribe(event=>{
      if(event.event!=='TEAM_UPDATED') return;
      const payload=event.payload as {teamId: number;version:number};
      if(payload.teamId!==this.data()?.teamId||payload.version<=(this.data()?.version||0)||this.saving()||this.confirming()) return;
      if(this.dirty()) {this.conflict.set(true);this.notice.set('Seu time mudou em outra aba. Recarregue antes de salvar.');}
      else this.load();
    });
  }
  load(): void {
    this.api.load(this.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:state=>{
      this.accept(state); this.conflict.set(false);this.error.set('');
      if(!this.connected) {
        this.showIntroduction.set(!state.introductionSeen);
        this.dark.set(readLocal<boolean>(this.themeKey(state.teamId))??false);
        const access=this.saves.access(this.id);
        if(access) this.realtime.connect({saveId:this.id,accessToken:access.accessToken,activity:'MANAGING_TEAM'});
        this.connected=true;
      }
    },error:e=>this.error.set(e.error?.message||'Não foi possível carregar a equipe. Volte ao saguão e tente novamente.')});
  }
  private accept(state:TeamManagement):void {if(this.data()?.teamId!==state.teamId)this.budgetCleared.set(false);this.data.set(state);this.tacticalPlan.set(defaultTacticalPlan(state.tacticalPlan));this.formation.set(state.formation);this.mentality.set(state.mentality);this.lineup.set(state.lineup);this.dirty.set(false);}
  toggleTheme(): void {this.dark.update(value=>!value);if(this.data())writeLocal(this.themeKey(this.data()!.teamId),this.dark());}
  changeFormation(value:string):void {if(this.locked())return;this.formation.set(value);this.tacticalPlan.update(p=>({...p,positions:{}}));this.lineup.set(suggestLineup(this.data()!.players,value));this.dirty.set(true);this.notice.set('Formação alterada. Confira os titulares e salve.');}
  changeSlot(event:{index:number;playerId: number}):void {if(this.locked())return;const old=this.lineup()[event.index];this.tacticalPlan.update(p=>{const positions={...p.positions};if(positions[old]){positions[event.playerId]=positions[old];delete positions[old];}return {...p,positions};});this.lineup.update(list=>list.map((id,index)=>index===event.index?event.playerId:id));this.dirty.set(true);this.notice.set('Escalação alterada. Salve para confirmar.');}
  selectPlayer(id:number):void {
    if(this.locked()||this.lineup().includes(id))return;
    const player=this.data()!.players.find(p=>p.id===id)!;
    if(!available(player))return;
    const candidates=roles(this.formation()).map((role,index)=>({role,index,ability:this.data()!.players.find(p=>p.id===this.lineup()[index])?.ability||0})).filter(slot=>positionFamily(slot.role)===player.position).sort((a,b)=>a.ability-b.ability);
    if(candidates[0])this.changeSlot({index:candidates[0].index,playerId:id});
  }
  autoLineup():void {if(this.locked())return;this.lineup.set(suggestLineup(this.data()!.players,this.formation()));this.tacticalPlan.update(p=>({...p,positions:{}}));this.dirty.set(true);this.notice.set('Melhores habilidades por posição selecionadas. Salve para confirmar.');}
  save():void {
    if(this.locked()||!this.valid()||this.conflict())return;
    this.saving.set(true);this.error.set('');
    this.api.update(this.id,{formation:this.formation(),mentality:this.mentality(),tacticalPlan:this.tacticalPlan(),lineup:this.lineup(),version:this.data()!.version}).pipe(finalize(()=>this.saving.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:state=>{this.accept(state);this.notice.set('Escalação e tática salvas na sua liga.');},error:e=>{this.error.set(e.error?.message||'Não foi possível salvar a equipe.');if(e.status===409)this.conflict.set(true);}});
  }
  ready():void {
    if(this.confirming()||this.dirty()||!this.valid())return;
    this.confirming.set(true);this.error.set('');
    const wasReady=!!this.me()?.ready;
    const initial:Observable<TeamManagement|null>=this.data()!.version===0&&!wasReady
      ?this.api.update(this.id,{formation:this.formation(),mentality:this.mentality(),lineup:this.lineup(),version:0})
      :of(null);
    initial.pipe(switchMap(state=>{if(state)this.accept(state);return this.saves.ready(this.id,!wasReady);}),finalize(()=>this.confirming.set(false)),takeUntilDestroyed(this.destroy)).subscribe({error:e=>this.error.set(e.error?.message||'Não foi possível confirmar a preparação.')});
  }
}
