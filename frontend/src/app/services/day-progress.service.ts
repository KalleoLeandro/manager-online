import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SaveService } from './save.service';
import { CalendarEvent } from './league-calendar.service';
export interface DayProgress {currentDate:string;nextMatch:string|null;advancedDays:number;stopReason:string;events:CalendarEvent[]}
@Injectable({providedIn:'root'})
export class DayProgressService {
 private readonly http=inject(HttpClient);private readonly saves=inject(SaveService);
 private headers(id:string){return {Authorization:'Bearer '+(this.saves.access(id)?.accessToken||'')};}
 state(id:string){return this.http.get<DayProgress>(`/api/saves/${id}/management/calendar/progress`,{headers:this.headers(id)});}
 advance(id:string,expectedDate:string,days:number){return this.http.post<DayProgress>(`/api/saves/${id}/management/calendar/progress`,{expectedDate,days},{headers:this.headers(id)});}
}
