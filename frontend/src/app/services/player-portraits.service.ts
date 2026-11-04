import { Injectable } from '@angular/core';

@Injectable({providedIn:'root'})
export class PlayerPortraitsService {
  readonly fallback='/api/players/generic/portrait';
  source(playerId: number): string { return `/api/players/${encodeURIComponent(String(playerId))}/portrait`; }
}
