import { Injectable, inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Subject } from "rxjs";
import { SaveService } from "./save.service";
export interface AwardPlayer {
  playerId: number;
  name: string;
  teamId: number;
  teamName: string;
  position: string;
  ability: number;
  appearances: number;
  ratedAppearances: number;
  minutes: number;
  goals: number;
  assists: number;
  passes: number;
  tackles: number;
  saves: number;
  cleanSheets: number;
  averageRating: number;
  estimatedMatches: number;
}
export interface AwardFinalist {
  place: number;
  player: AwardPlayer;
  score: number;
}
export interface PlayerCeremony {
  id: number;
  key: string;
  scope: string;
  year: number;
  title: string;
  competition: string;
  date: string;
  trophyId: number;
  criteria: string;
  finalists: AwardFinalist[];
}
@Injectable({ providedIn: "root" })
export class PlayerAwardsService {
  private readonly http = inject(HttpClient);
  private readonly saves = inject(SaveService);
  private readonly requests = new Subject<{ leagueId: string; awardId: number }>();
  readonly replay$ = this.requests.asObservable();
  private options(id: string) {
    return { headers: { Authorization: `Bearer ${this.saves.access(id)?.accessToken || ""}` } };
  }
  private url(id: string) {
    return `/api/saves/${id}/player-awards`;
  }
  pending(id: string) {
    return this.http.get<PlayerCeremony[]>(this.url(id) + "/pending", this.options(id));
  }
  history(id: string) {
    return this.http.get<PlayerCeremony[]>(this.url(id), this.options(id));
  }
  detail(id: string, award: number) {
    return this.http.get<PlayerCeremony>(this.url(id) + "/" + award, this.options(id));
  }
  seen(id: string, award: number) {
    return this.http.post<void>(this.url(id) + "/" + award + "/seen", {}, this.options(id));
  }
  replay(leagueId: string, awardId: number) {
    this.requests.next({ leagueId, awardId });
  }
}
