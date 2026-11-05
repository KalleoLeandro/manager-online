import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SaveService } from './save.service';
export interface CalendarEvent {id:string;date:string;type:string;title:string;description:string;status:string;opponentTeamId:number|null;opponentTeamName:string|null;venue:'C'|'V'|null}
export interface LeagueCalendar {currentDate:string;weekStart:string;weekEnd:string;events:CalendarEvent[]}
export interface SeasonCalendar {currentDate:string;seasonStart:string;seasonEnd:string;seasonLabel:string;events:CalendarEvent[]}
@Injectable({providedIn:'root'})
export class LeagueCalendarService {
  private readonly http=inject(HttpClient);private readonly saves=inject(SaveService);
  season(id:string){return this.http.get<SeasonCalendar>(`/api/saves/${id}/management/calendar/season`,{headers:{Authorization:`Bearer ${this.saves.access(id)?.accessToken||''}`}});}
  week(id:string){return this.http.get<LeagueCalendar>(`/api/saves/${id}/management/calendar`,{headers:{Authorization:`Bearer ${this.saves.access(id)?.accessToken||''}`}});}
}
