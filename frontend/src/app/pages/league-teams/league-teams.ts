import { Component, DestroyRef, ElementRef, OnInit, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, finalize } from 'rxjs';
import { LeagueTeam, LeagueTeamDetails, LeagueTeams, LeagueTeamsService } from '../../services/league-teams.service';
import { Member } from '../../services/save.service';
import { ClubCrest } from '../../components/club-crest/club-crest';
import { TeamRoster } from '../../components/team-roster/team-roster';

const normalize = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase();

@Component({selector:'app-league-teams',imports:[FormsModule,ClubCrest,TeamRoster],templateUrl:'./league-teams.html',styleUrl:'./league-teams.css'})
export class LeagueTeamsPage implements OnInit {
  readonly saveId = input.required<string>();
  readonly members = input<Member[]>([]);
  readonly back = output<void>();
  private readonly api = inject(LeagueTeamsService);
  private readonly destroy = inject(DestroyRef);
  private readonly detailElement = viewChild<ElementRef<HTMLElement>>('clubDetail');
  private request?: Subscription;
  private readonly cache = new Map<number,LeagueTeamDetails>();
  readonly data = signal<LeagueTeams|null>(null);
  readonly query = signal('');
  readonly competition = signal<number|null>(null);
  readonly selectedId = signal<number|null>(null);
  readonly detail = signal<LeagueTeamDetails|null>(null);
  readonly loading = signal(true);
  readonly rosterLoading = signal(false);
  readonly error = signal('');
  readonly rosterError = signal('');
  readonly competitions = computed(() => [...new Map((this.data()?.teams || []).map(t => [t.leagueId,t])).values()]);
  readonly visible = computed(() => (this.data()?.teams || []).filter(t => t.id !== this.data()?.ownTeamId &&
    (this.competition() === null || t.leagueId === this.competition()) && normalize(t.name).includes(normalize(this.query().trim()))));

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading.set(true);this.error.set('');this.cache.clear();
    this.request?.unsubscribe();this.detail.set(null);this.selectedId.set(null);this.rosterError.set('');
    this.api.teams(this.saveId()).pipe(finalize(()=>this.loading.set(false)),takeUntilDestroyed(this.destroy)).subscribe({
      next: data => {const first=!this.data();this.data.set(data);if(first)this.competition.set(data.ownLeagueId);const team=this.visible()[0];if(team)this.select(team);},
      error: e => this.error.set(e.error?.message || 'Não foi possível consultar os clubes. Tente novamente.')
    });
  }
  select(team: LeagueTeam, scrollToRoster = false): void {
    if(this.selectedId()===team.id && this.detail()){this.showRoster(scrollToRoster);return;}
    this.request?.unsubscribe();this.selectedId.set(team.id);this.detail.set(null);this.rosterError.set('');
    const cached=this.cache.get(team.id);if(cached){this.detail.set(cached);this.rosterLoading.set(false);this.showRoster(scrollToRoster);return;}
    this.rosterLoading.set(true);
    this.request=this.api.team(this.saveId(),team.id).pipe(finalize(()=>this.rosterLoading.set(false)),takeUntilDestroyed(this.destroy)).subscribe({
      next: detail => {this.cache.set(team.id,detail);this.detail.set(detail);this.showRoster(scrollToRoster);},
      error: e => this.rosterError.set(e.error?.message || 'Não foi possível carregar este elenco. Selecione o clube para tentar novamente.')
    });
  }
  private showRoster(requested: boolean): void {
    if(!requested || !window.matchMedia('(max-width: 800px)').matches)return;
    requestAnimationFrame(()=>{
      if(!this.destroy.destroyed)this.detailElement()?.nativeElement.scrollIntoView({block:'start',behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'});
    });
  }
  manager(team: LeagueTeam): string { return this.members().find(m=>m.teamId===team.id)?.name || team.managerName || 'Sem treinador humano'; }
}
