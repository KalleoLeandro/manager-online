import { Injectable } from '@angular/core';
import { MatchIncidentTracker } from '../utils/match-incidents';

@Injectable({providedIn:'root'})
export class MatchIncidentsService {
 private readonly leagues=new Map<string,MatchIncidentTracker>();
 forLeague(id:string):MatchIncidentTracker {
  let tracker=this.leagues.get(id);
  if(!tracker){tracker=new MatchIncidentTracker();this.leagues.set(id,tracker);}
  return tracker;
 }
}
