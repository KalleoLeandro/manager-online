import { PlayerTechnical, PlayerCondition } from '../utils/player-attributes';
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { shareReplay, switchMap } from 'rxjs';

export interface League { id: number; name: string; country: string; season: string; teamCount: number; division: number }
export interface Team { id: number; leagueId: number; name: string; shortName: string; abbreviation: string; crestUrl: string | null; rating: number; nationalRank: number; selectable: boolean; groupName: string | null }
export interface Player { id: number; name: string; position: string;positions?:string[];positionsEstimated?:boolean; level: string; ability: number; attribute1: string; value1: number; attribute2: string; value2: number; age: number | null; number: string | null; photoUrl: string | null; nationality: string | null; generated: boolean; generationOrigin: string | null; technical?: PlayerTechnical; condition?: PlayerCondition }
export interface RosterPolicy { country: string; season: string; leagueIds: number[]; reference: string; windowStart: string; windowEnd: string; sourceUrl: string; verification: string; note: string }
export interface Catalog { version: string; source: string; ratingMethod: string; leagues: League[]; teams: Team[]; internationalTeams: number; rosterPolicies: RosterPolicy[] }

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);
  readonly catalog$ = this.http.get<Catalog>('/api/catalog', { headers: { 'Cache-Control': 'no-cache' } }).pipe(shareReplay(1));
  private readonly rosters = new Map<number, ReturnType<typeof this.loadRoster>>();
  private loadRoster(id: number) {
    return this.catalog$.pipe(switchMap(catalog => this.http.get<Player[]>(`/api/catalog/teams/${encodeURIComponent(String(id))}/players`, { params: { v: catalog.version } })),shareReplay(1));
  }
  players(id: number) {
    if (!this.rosters.has(id)) this.rosters.set(id, this.loadRoster(id));
    return this.rosters.get(id)!;
  }
}
