import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SaveService } from './save.service';
export interface PlayerContract {playerId:number;name:string;teamId:number|null;teamName:string|null;position:string;ability:number;age:number|null;marketValue:number;salaryMonthly:number;expectedSalary:number;startDate:string;endDate:string;signingBonus:number;appearanceBonus:number;goalBonus:number;titleBonus:number;estimated:boolean;humanClub:boolean}
export interface SeasonPlan {id:number;seasonId:number;approved:boolean;promisedRank:number;baseFunding:number;transferBudget:number;salaryBudget:number;infrastructureBudget:number;miscellaneousBudget:number;reserveBudget:number;transferSpent:number;finalRank:number|null;promiseMet:boolean|null;settled:boolean}
export interface BudgetTerms {seasonId:number;promisedRank:number;transferBudget:number;salaryBudget:number;infrastructureBudget:number;miscellaneousBudget:number;reserveBudget:number}
export interface FinanceState {date:string;teamId:number;currency:string;annualRevenue:number;season:{id:number;year:number;label:string;competitionName:string;country:string;division:number;teamCount:number;firstDate:string;lastDate:string};plan:SeasonPlan;cash:number;payrollMonthly:number;staffSalaryMonthly:number;committedWagesMonthly:number;reservedTransfers:number;transferAvailable:number;boardTrust:number;fanTrust:number;expectedRank:number;objectives:{rank:number;label:string;maximumFunding:number;boardTrustDelta:number;fanTrustDelta:number;reaction:string}[];history:{seasonLabel:string;promisedRank:number;finalRank:number;promiseMet:boolean}[];ledger:{id:number;date:string;category:string;description:string;amount:number}[];approvalRequired:boolean}
export interface OfferTerms {playerId:number;fee:number;salaryMonthly:number;months:number;signingBonus:number;appearanceBonus:number;goalBonus:number;titleBonus:number}
export interface TransferOffer extends OfferTerms {id:number;playerName:string;buyerId:number;buyerName:string;sellerId:number|null;sellerName:string|null;seasonId:number;status:string;clubMeter:number;playerMeter:number;offeredDate:string;decisionDate:string|null;expiresDate:string;sellerApproved:boolean;feedback:string;revision:number}
@Injectable({providedIn:'root'})
export class FinanceService {
 private readonly http=inject(HttpClient);private readonly saves=inject(SaveService);
 private options(id:string){return {headers:{Authorization:'Bearer '+(this.saves.access(id)?.accessToken||'')}};}
 state(id:string){return this.http.get<FinanceState>(`/api/saves/${id}/management/finance`,this.options(id));}
 approve(id:string,terms:BudgetTerms){return this.http.post<FinanceState>(`/api/saves/${id}/management/finance/season`,terms,this.options(id));}
 contracts(id:string){return this.http.get<PlayerContract[]>(`/api/saves/${id}/management/finance/contracts`,this.options(id));}
 completed(id:string){return this.http.get<TransferOffer[]>(`/api/saves/${id}/management/transfers/completed`,this.options(id));}
 offers(id:string){return this.http.get<TransferOffer[]>(`/api/saves/${id}/management/transfers`,this.options(id));}
 player(id:string,player:number){return this.http.get<PlayerContract>(`/api/saves/${id}/management/transfers/players/${player}`,this.options(id));}
 market(id:string,query:string){return this.http.get<PlayerContract[]>(`/api/saves/${id}/management/transfers/market`,{...this.options(id),params:{query}});}
 offer(id:string,terms:OfferTerms,improve?:number){return this.http.post<TransferOffer>(`/api/saves/${id}/management/transfers${improve?'/'+improve+'/improve':''}`,terms,this.options(id));}
 cancel(id:string,offer:number){return this.http.post<TransferOffer>(`/api/saves/${id}/management/transfers/${offer}/cancel`,{},this.options(id));}
 decide(id:string,offer:TransferOffer,accept:boolean){return this.http.post<TransferOffer>(`/api/saves/${id}/management/transfers/${offer.id}/club-decision`,{accept,revision:offer.revision},this.options(id));}
}
