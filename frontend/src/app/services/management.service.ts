import { TacticalPlan } from '../utils/tactical-plan';
import { PlayerTechnical, PlayerCondition } from '../utils/player-attributes';
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Room, SaveService } from './save.service';

export interface ManagedPlayer { id: number; name: string; position: string; positions?:string[];positionsEstimated?:boolean; level: string; ability: number; attribute1: string; value1: number; attribute2: string; value2: number; age: number | null; number: string | null; photoUrl: string | null; fitness: number; generated: boolean; generationOrigin: string | null; nationality?: string | null; technical?: PlayerTechnical; condition?: PlayerCondition }
export interface TeamManagement {
  room: Room; memberId: string; teamId: number; teamName: string; crestUrl: string | null; leagueName: string;
  introductionSeen: boolean; tacticalPlan:TacticalPlan;
  teamRating: number; budget: number; points: number; palette: { primary: string; secondary: string; awayVerified: boolean; awayAccent?: string; awaySource?: string | null; homeAccent?: string; homeColors?: string[]; homeSource?: string | null; homeMethod?: 'official' | 'crest' | 'neutral' };
  players: ManagedPlayer[]; formation: string; mentality: string; lineup: number[]; version: number;
}
export interface TeamUpdate { tacticalPlan?:TacticalPlan; formation: string; mentality: string; lineup: number[]; version: number }
@Injectable({ providedIn: 'root' })
export class ManagementService {
  private readonly http=inject(HttpClient);
  private readonly saves=inject(SaveService);
  private options(id: string) { return { headers: { Authorization: `Bearer ${this.saves.access(id)?.accessToken || ''}` } }; }
  load(id: string) { return this.http.get<TeamManagement>(`/api/saves/${id}/management`,this.options(id)); }
  acknowledgeIntroduction(id:string){return this.http.put<void>('/api/saves/'+id+'/management/introduction',{},this.options(id));}
  juniors(id: string) { return this.http.get<ManagedPlayer[]>(`/api/saves/${id}/management/juniors`,this.options(id)); }
  search(id:string,query:string){return this.http.get<{player:ManagedPlayer;teamId:number;teamName:string}[]>(`/api/saves/${id}/management/players`,{...this.options(id),params:{query}});}
  update(id: string,input: TeamUpdate) { return this.http.put<TeamManagement>(`/api/saves/${id}/management`,input,this.options(id)); }
}
