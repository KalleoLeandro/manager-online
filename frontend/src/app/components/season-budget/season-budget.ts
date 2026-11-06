import { Component, DestroyRef, ElementRef, HostListener, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { BudgetTerms, FinanceService, FinanceState } from '../../services/finance.service';
import { ClubCrest } from '../club-crest/club-crest';
import { GameIcon } from '../game-icon/game-icon';
import { MoneyInput } from '../money-input/money-input';
import { TrophyImage } from '../trophy-image/trophy-image';
import { seasonTrophies } from '../../utils/trophies';
@Component({selector:'app-season-budget',imports:[FormsModule,CurrencyPipe,ClubCrest,GameIcon,TrophyImage,MoneyInput],templateUrl:'./season-budget.html',styleUrl:'./season-budget.css'})
export class SeasonBudget {
 readonly showSummary=input(true);
 readonly leagueId=input.required<string>();readonly teamId=input.required<number>();readonly teamName=input('');readonly revision=input(0);readonly round=input(0);readonly suspended=input(false);readonly disabled=input(false);readonly changed=output<void>();readonly finances=output<void>();readonly clearance=output<boolean>();
 private readonly api=inject(FinanceService);private readonly destroy=inject(DestroyRef);private readonly dialog=viewChild<ElementRef<HTMLDialogElement>>('dialog');
 readonly data=signal<FinanceState|null>(null);readonly error=signal('');readonly busy=signal(false);readonly loading=signal(false);
 readonly terms=signal<BudgetTerms>({seasonId:0,promisedRank:1,transferBudget:0,salaryBudget:0,infrastructureBudget:0,miscellaneousBudget:0,reserveBudget:0});
 readonly total=computed(()=>{const t=this.terms();return t.transferBudget+t.salaryBudget+t.infrastructureBudget+t.miscellaneousBudget+t.reserveBudget;});
 readonly trophies=computed(()=>{const season=this.data()?.season;return seasonTrophies(season?.country||'',season?.division||1,season?.competitionName||'');});
 readonly selectedObjective=computed(()=>this.data()?.objectives.find(o=>o.rank===this.terms().promisedRank));
 readonly confidence=computed(()=>{const d=this.data(),o=this.selectedObjective();return {board:Math.max(0,Math.min(100,(d?.boardTrust||0)+(d?.plan.approved?0:o?.boardTrustDelta||0))),fans:Math.max(0,Math.min(100,(d?.fanTrust||0)+(d?.plan.approved?0:o?.fanTrustDelta||0)))};});
 readonly limit=computed(()=>this.data()?.objectives.find(o=>o.rank===this.terms().promisedRank)?.maximumFunding||0);
 readonly categories:{key:keyof Pick<BudgetTerms,'salaryBudget'|'transferBudget'|'infrastructureBudget'|'miscellaneousBudget'|'reserveBudget'>;label:string;color:string}[]=[{key:'salaryBudget',label:'Salários e bônus (ano)',color:'#3977a2'},{key:'transferBudget',label:'Transferências e luvas',color:'#3b8e9d'},{key:'infrastructureBudget',label:'Infraestrutura',color:'#769740'},{key:'miscellaneousBudget',label:'Outras despesas',color:'#b59c42'},{key:'reserveBudget',label:'Reserva',color:'#b65e4b'}];
 constructor(){effect(cleanup=>{const id=this.leagueId();this.teamId();this.revision();this.round();const request=this.api.state(id).subscribe({next:d=>this.accept(d),error:e=>this.error.set(e.error?.message||'Não foi possível carregar o orçamento.')});cleanup(()=>request.unsubscribe());});effect(()=>{const dialog=this.dialog()?.nativeElement,d=this.data();if(dialog&&d?.approvalRequired&&!this.suspended()&&!dialog.open)dialog.showModal();});}
 private accept(d:FinanceState):void{const changed=this.data()?.plan.id!==d.plan.id;this.data.set(d);this.clearance.emit(!d.approvalRequired);if(changed){this.terms.set({seasonId:d.plan.seasonId,promisedRank:d.plan.promisedRank,transferBudget:d.plan.transferBudget,salaryBudget:d.plan.salaryBudget,infrastructureBudget:d.plan.infrastructureBudget,miscellaneousBudget:d.plan.miscellaneousBudget,reserveBudget:d.plan.reserveBudget});if(!d.plan.approved)this.recommend();}}
 recommend():void{const d=this.data();if(!d||d.plan.approved)return;const total=Math.floor(this.limit()*.95),salary=Math.max(d.committedWagesMonthly*12,Math.min(Math.ceil(d.committedWagesMonthly*12*1.15),Math.floor(total*.78))),reserve=Math.ceil(total*.10),remaining=Math.max(0,total-salary-reserve),infra=Math.floor(remaining*.15),misc=Math.floor(remaining*.10);this.terms.update(t=>({...t,salaryBudget:salary,reserveBudget:reserve,infrastructureBudget:infra,miscellaneousBudget:misc,transferBudget:Math.max(0,total-salary-reserve-infra-misc)}));}
 amount(key:typeof this.categories[number]['key'],value:number):void{this.terms.update(t=>({...t,[key]:Math.max(0,Math.round(Number(value)||0))}));}
 objective(rank:number):void{this.terms.update(t=>({...t,promisedRank:Number(rank)}));this.recommend();}
 open():void{this.dialog()?.nativeElement.showModal();}
 close():void{if(!this.data()?.approvalRequired||this.suspended())this.dialog()?.nativeElement.close();}
 cancel(event:Event):void{if(this.data()?.approvalRequired&&!this.suspended())event.preventDefault();}
 @HostListener('document:keydown',['$event'])
 protectRequiredBudget(event:KeyboardEvent):void{if(event.key==='Escape'&&this.dialog()?.nativeElement.open&&this.data()?.approvalRequired&&!this.suspended()){event.preventDefault();event.stopPropagation();}}
 approve():void{if(this.busy()||this.disabled())return;this.error.set('');this.busy.set(true);this.api.approve(this.leagueId(),this.terms()).pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:d=>{this.data.set(d);this.clearance.emit(!d.approvalRequired);this.dialog()?.nativeElement.close();this.changed.emit();},error:e=>this.error.set(e.error?.message||'A diretoria não aprovou esta divisão de verbas.')});}
}
