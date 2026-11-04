import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SaveService } from './save.service';
import { Player } from './catalog.service';

export interface LeagueTeam {
  id: number; leagueId: number; leagueName: string; country: string; division: number;
  name: string; shortName: string; crestUrl: string; rating: number; playerCount: number; managerName: string | null;
}
export interface LeagueTeams { ownTeamId: number; ownLeagueId: number; teams: LeagueTeam[] }
export interface LeagueTeamDetails { team: LeagueTeam; players: Player[] }

@Injectable({ providedIn: 'root' })
export class LeagueTeamsService {
  private readonly http = inject(HttpClient);
  private readonly saves = inject(SaveService);
  private options(id: string) { return { headers: { Authorization: `Bearer ${this.saves.access(id)?.accessToken || ''}` } }; }
  teams(id: string) { return this.http.get<LeagueTeams>(`/api/saves/${id}/teams`, this.options(id)); }
  team(id: string, teamId: number) { return this.http.get<LeagueTeamDetails>(`/api/saves/${id}/teams/${teamId}`, this.options(id)); }
}
