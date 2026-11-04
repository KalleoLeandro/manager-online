import { inject, Injectable } from '@angular/core';import { HttpClient } from '@angular/common/http';import { SaveService } from './save.service';
export interface NationalSeason {id:number;year:number;label:string;competitionName:string;country:string;division:number;teamCount:number;rounds:number;firstDate:string;lastDate:string;format:string}
export interface NationalFixture {id:number;round:number;date:string;homeTeamId:number;homeTeamName:string;awayTeamId:number;awayTeamName:string;status:string;homeGoals:number|null;awayGoals:number|null}
export interface NationalChampionship {ownTeamId:number;season:NationalSeason;seasons:NationalSeason[];fixtures:NationalFixture[]}
@Injectable({providedIn:'root'})export class NationalChampionshipService {
 private readonly http=inject(HttpClient);private readonly saves=inject(SaveService);
 load(id:string,year?:number){return this.http.get<NationalChampionship>(`/api/saves/${id}/management/national-championship`,{headers:{Authorization:`Bearer ${this.saves.access(id)?.accessToken||''}`},params:year===undefined?{}:{year}});}
}
