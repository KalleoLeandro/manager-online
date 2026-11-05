import {inject,Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {SaveService} from './save.service';
export interface DrawClub {id:number;name:string;venue:string}
export interface TournamentDraw {id:number;competition:string;stage:string;date:string;startedAt:number;rows:{club:DrawClub;opponents:DrawClub[]}[]}
@Injectable({providedIn:'root'})
export class TournamentDrawService {
 private readonly http=inject(HttpClient);private readonly saves=inject(SaveService);
 private options(id:string){return {headers:{Authorization:'Bearer '+(this.saves.access(id)?.accessToken||'')}};}
 pending(id:string){return this.http.get<TournamentDraw[]>(`/api/saves/${id}/draws`,this.options(id));}
 acknowledge(id:string,draw:number){return this.http.post<void>(`/api/saves/${id}/draws/${draw}/seen`,{},this.options(id));}
}
