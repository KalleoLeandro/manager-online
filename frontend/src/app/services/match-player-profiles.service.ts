import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, shareReplay, throwError } from 'rxjs';
import { LeagueTeamsService } from './league-teams.service';
import { PlayerProfile } from '../components/player-details/player-details';

@Injectable()
export class MatchPlayerProfilesService {
 private readonly teams=inject(LeagueTeamsService);
 private readonly cache=new Map<string,Observable<PlayerProfile[]>>();
 squad(leagueId:string,teamId:number):Observable<PlayerProfile[]>{
  const key=leagueId+':'+teamId;
  if(!this.cache.has(key))this.cache.set(key,this.teams.team(leagueId,teamId).pipe(
   map(data=>data.players as PlayerProfile[]),
   catchError(error=>{this.cache.delete(key);return throwError(()=>error);}),
   shareReplay({bufferSize:1,refCount:false})
  ));
  return this.cache.get(key)!;
 }
}
