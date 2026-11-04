import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SaveService } from './save.service';
export interface CalendarEvent {id:string;date:string;type:string;title:string;description:string;status:string}
export interface LeagueCalendar {currentDate:string;weekStart:string;weekEnd:string;events:CalendarEvent[]}
@Injectable({providedIn:'root'})
export class LeagueCalendarService {
  private readonly http=inject(HttpClient);private readonly saves=inject(SaveService);
  week(id:string){return this.http.get<LeagueCalendar>(`/api/saves/${id}/management/calendar`,{headers:{Authorization:`Bearer ${this.saves.access(id)?.accessToken||''}`}});}
}
