import { TacticalPlan } from '../utils/tactical-plan';
import { switchMap } from 'rxjs';
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SaveService } from './save.service';

export interface MatchStats {possessionTicks:number;passesCompleted:number;passesMissed:number;shots:number;shotsOnTarget:number;saves:number;corners:number;throwIns:number;fouls:number;yellows:number;reds:number;offsides:number;injuries:number;penalties:number;penaltiesScored:number;expectedGoals:number;tackles:number;interceptions:number;blockedShots:number}
export interface MatchPlayer {rating?:number|null;morale?:number;sharpness?:number;lastTalkPeriod?:number;talkReaction?:string|null;number?:string|null;id:number;name:string;position:string;naturalPosition?:string;assignedPosition?:string;preferredPositions?:string[];slot?:number;ability:number;fitness:number;onPitch:boolean;entered:boolean;withdrawn:boolean;sentOff:boolean;injured:boolean;yellow:number;minutes:number;goals:number;assists:number;injuryDays:number;attributes:Record<string,number>}
export type TouchlineCommand = 'ENCOURAGE'|'DEMAND'|'CALM'|'COMPLAIN';
export interface TouchlineState {refereeConfidence:number;warnings:number;complaints:number;cooldownUntil:number;effectUntil:number;lastComplaint:number;dismissed:boolean;command:string;refereeMessage:string}
export interface MatchTeam {touchline?:TouchlineState;tacticalPlan?:TacticalPlan;id:number;name:string;human:boolean;formation:string;mentality:string;goals:number;substitutions:number;substitutionWindows:number;pauseCount:number;penaltyTaker:number|null;players:MatchPlayer[];stats:MatchStats}
export interface MatchEvent {sequence:number;minute:number;period:number;type:string;teamId:number;playerId:number|null;relatedPlayerId:number|null;text:string}
export interface VarReview {goalSequence:number;teamId:number;phase:'REVIEW'|'DECISION';remainingMillis:number;confirmed:boolean|null;reason:string|null}
export interface MatchView {halfTimeBreak?:boolean;competitionName?:string;homePenalties?:number|null;awayPenalties?:number|null;cupMatch?:boolean;fixtureId:number;revision:number;minute:number;period:number;addedFirst:number;addedSecond:number;status:string;pauseReason:string|null;pausedUntil:number;canPause:boolean;penaltyTeam:number|null;home:MatchTeam;away:MatchTeam;events:MatchEvent[];goalCelebrationRemainingMillis:number;celebratingGoalSequence:number;awaitingCoachDecision:boolean;canResume:boolean;pendingCoachTeams:number[];varReview:VarReview|null}
export interface MatchSummary {fixtureId:number;homeId:number;homeName:string;awayId:number;awayName:string;homeGoals:number;awayGoals:number;minute:number;period:number;status:string;humanVsHuman:boolean;revision:number}
export interface MatchTicket {fixtureId:number;date:string;competition:string;homeId:number;homeName:string;homeColor:string;homeSecondary:string;awayId:number;awayName:string;awayColor:string;awaySecondary:string}
export interface Matchday {ticket?:MatchTicket|null;speedPercent:number;canChangeSpeed:boolean;localMultiplayer:boolean;date:string;ownTeamId:number;leagueStatus:string;matches:MatchSummary[];selected:MatchView|null;participants:{name:string;teamId:number;teamName:string;status:'PLAYING'|'FINISHED'|'BYE';fixtureId:number|null}[]}
export interface MatchAction {tacticalPlan?:TacticalPlan;type:TouchlineCommand|'SUBSTITUTE'|'SWAP_POSITIONS'|'TALK_PRAISE'|'TALK_ENCOURAGE'|'TALK_CRITICIZE'|'SET_KEEPER'|'TACTICS'|'PENALTY_TAKER'|'OPEN_TACTICS'|'PAUSE'|'RESUME';playerOut?:number;playerIn?:number;formation?:string;mentality?:string;revision:number}
@Injectable({providedIn:'root'})
export class MatchdayService {
 private readonly http=inject(HttpClient);private readonly saves=inject(SaveService);
 private headers(id:string){return {Authorization:`Bearer ${this.saves.access(id)?.accessToken||''}`};}
 load(id:string,fixture?:number){return this.http.get<Matchday>(`/api/saves/${id}/matchday`,{headers:this.headers(id),params:fixture?{fixture}:{}});}
 speed(id:string,percent:number,fixture?:number){const owner=this.saves.localOwner(id);return this.http.put<Matchday>(`/api/saves/${id}/matchday/speed`,{percent},{headers:owner?{Authorization:'Bearer '+owner.accessToken}:this.headers(id),params:fixture?{fixture}:{}}).pipe(switchMap(()=>this.load(id,fixture)));}
 start(id:string){return this.http.post<Matchday>(`/api/saves/${id}/matchday/start`,{},{headers:this.headers(id)});}
 action(id:string,fixture:number,action:MatchAction){return this.http.post<Matchday>(`/api/saves/${id}/matchday/${fixture}/actions`,action,{headers:this.headers(id)});}
}
