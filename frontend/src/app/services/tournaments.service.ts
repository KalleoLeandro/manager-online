import { inject, Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { SaveService } from "./save.service";
import { retry, timer, throwError } from "rxjs";
export interface CupStage {
  key: string;
  label: string;
  entrants: number;
  newEntrants: number;
  legs: number;
  format: string;
  firstDate: string;
  secondDate: string | null;
}
export interface CupEdition {
  id: number;
  key: string;
  year: number;
  label: string;
  name: string;
  country: string;
  status: string;
  currentStage: number;
  championId: number | null;
  runnerUpId: number | null;
  rules: { version: string; source: string; participants: number; stages: CupStage[] };
}
export interface CupTeam {
  id: number;
  name: string;
  association: string;
  stage: string;
  rank: number;
}
export interface CupGame {
  id: number;
  stage: number;
  number: number;
  leg: number;
  date: string;
  home: number;
  away: number;
  homeGoals: number | null;
  awayGoals: number | null;
  status: string;
}
export interface CupStanding {
  stage: number;
  group: string;
  position: number;
  teamId: number;
  name: string;
  played: number;
  wins: number;
  draws: number;
  losses: number;
  gf: number;
  ga: number;
  points: number;
}
export interface CupDetail {
  edition: CupEdition;
  teams: CupTeam[];
  ties: { stage: number; number: number; home: number; away: number; winner: number | null }[];
  fixtures: CupGame[];
  groups: Record<string, string>;
  standings: CupStanding[];
}
export interface ClubTitle {
  editionId: number;
  competition: string;
  year: number;
  label: string;
  stage: string;
  trophyId: number;
  date: string;
}
@Injectable({ providedIn: "root" })
export class TournamentsService {
  private readonly http = inject(HttpClient);
  private readonly saves = inject(SaveService);
  private options(id: string) {
    return { headers: { Authorization: "Bearer " + (this.saves.access(id)?.accessToken || "") } };
  }
  list(id: string) {
    return this.http.get<CupEdition[]>(`/api/saves/${id}/tournaments`, this.options(id)).pipe(
      retry({count:3,delay:(error,attempt)=>error.status===503?timer(attempt*750):throwError(()=>error)})
    );
  }
  detail(id: string, edition: number) {
    return this.http.get<CupDetail>(`/api/saves/${id}/tournaments/${edition}`, this.options(id));
  }
  titles(id: string, team: number) {
    return this.http.get<ClubTitle[]>(`/api/saves/${id}/tournaments/titles`, {
      ...this.options(id),
      params: { team },
    });
  }
}
