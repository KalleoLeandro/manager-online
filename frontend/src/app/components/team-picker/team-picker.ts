import { Component, computed, input, output, signal } from '@angular/core';
import { ClubCrest } from '../club-crest/club-crest';
import { League, Team } from '../../services/catalog.service';

@Component({ selector: 'app-team-picker', imports: [ClubCrest], templateUrl: './team-picker.html', styleUrl: './team-picker.css' })
export class TeamPicker {
  readonly teams = input.required<Team[]>();
  readonly leagues = input.required<League[]>();
  readonly selected = input<number | null>(null);
  readonly occupied = input<number[]>([]);
  readonly choose = output<number>();
  private readonly selectedLeague = signal<number | null>(null);
  readonly league = computed(() => this.selectedLeague() ?? this.leagues().find(l => l.country === 'Brasil' && l.division === 1)?.id ?? this.leagues()[0]?.id ?? null);
  readonly country = computed(() => this.leagues().find(l => l.id === this.league())?.country);
  readonly search = signal('');
  readonly group = signal('');
  readonly countries = computed(() => this.leagues().filter(l => l.division === 1));
  readonly divisions = computed(() => this.leagues().filter(l => l.country === this.country()).sort((a,b) => a.division-b.division));
  readonly groups = computed(() => [...new Set(this.teams().filter(t => t.leagueId === this.league()).map(t => t.groupName).filter((v): v is string => !!v))].sort());
  selectLeague(id: number) { this.selectedLeague.set(id); this.search.set(''); this.group.set(''); }
  readonly visible = computed(() => this.teams().filter(t => t.leagueId === this.league() && (!this.group() || t.groupName === this.group()) && t.name.toLocaleLowerCase().includes(this.search().toLocaleLowerCase())));
}
