import { Component, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { FinanceService, FinanceState, PlayerContract } from '../../services/finance.service';
import { PlayerPortrait } from '../../components/player-portrait/player-portrait';
@Component({selector:'app-club-finance',imports:[CurrencyPipe,PlayerPortrait],templateUrl:'./club-finance.html',styleUrl:'./club-finance.css'})
export class ClubFinancePage {
 readonly leagueId=input.required<string>();readonly revision=input(0);readonly budgetRequested=output<void>();readonly negotiate=output<number>();
 private readonly api=inject(FinanceService);private readonly destroy=inject(DestroyRef);
 readonly data=signal<FinanceState|null>(null);readonly contracts=signal<PlayerContract[]>([]);readonly error=signal('');
 constructor(){effect(cleanup=>{const id=this.leagueId();this.revision();const request=forkJoin({data:this.api.state(id),contracts:this.api.contracts(id)}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{this.data.set(r.data);this.contracts.set(r.contracts);},error:e=>this.error.set(e.error?.message||'Não foi possível carregar as finanças.')});cleanup(()=>request.unsubscribe());});}
}
