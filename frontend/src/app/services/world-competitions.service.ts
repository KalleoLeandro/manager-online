import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SaveService } from './save.service';
import { NationalFixture } from './national-championship.service';
export interface WorldCompetition {id:number;leagueId:number;year:number;label:string;name:string;country:string;division:number;teams:number;played:number;scheduled:number;firstDate:string;lastDate:string;historical:boolean;sourceUrl:string|null}
export interface WorldStanding {groupName:string|null;teamId:number;name:string;played:number;wins:number;draws:number;losses:number;goalsFor:number;goalsAgainst:number;points:number}
export interface WorldChampionship {competition:WorldCompetition;table:WorldStanding[];fixtures:NationalFixture[]}
@Injectable({providedIn:'root'})
export class WorldCompetitionsService {
 private readonly http=inject(HttpClient);private readonly saves=inject(SaveService);
 private options(id:string){return {headers:{Authorization:'Bearer '+(this.saves.access(id)?.accessToken||'')}};}
 overview(id:string){return this.http.get<{date:string;competitions:WorldCompetition[]}>(`/api/saves/${id}/competitions`,this.options(id));}
 championship(id:string,season:number){return this.http.get<WorldChampionship>(`/api/saves/${id}/competitions/${season}`,this.options(id));}
}
