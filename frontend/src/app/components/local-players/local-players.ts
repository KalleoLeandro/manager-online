import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { Room, SaveService } from '../../services/save.service';
import { Catalog, CatalogService } from '../../services/catalog.service';
import { GameIcon } from '../game-icon/game-icon';

@Component({selector:'app-local-players',imports:[FormsModule,GameIcon],templateUrl:'./local-players.html',styleUrl:'./local-players.css'})
export class LocalPlayers {
 readonly leagueId=input.required<string>();readonly room=input<Room|null>(null);readonly setup=input(false);readonly disabled=input(false);
 readonly saves=inject(SaveService);private readonly destroy=inject(DestroyRef);
 readonly catalog=signal<Catalog|null>(null);readonly busy=signal(false);readonly error=signal('');readonly notice=signal('');
 readonly active=computed(()=>this.saves.access(this.leagueId())?.memberId||'');
 readonly available=computed(()=>this.catalog()?.teams.filter(t=>t.selectable&&t.leagueId===this.league()&&!this.room()?.members.some(m=>m.teamId===t.id))||[]);
 managerName='';teamId=0;readonly league=signal(0);
 constructor(){inject(CatalogService).catalog$.pipe(takeUntilDestroyed()).subscribe({next:c=>{this.catalog.set(c);this.league.set(c.leagues[0]?.id||0);},error:()=>this.error.set('Não foi possível carregar os clubes.')});}
 switch(memberId:string):void{if(this.busy()||this.disabled()||memberId===this.active())return;this.busy.set(true);this.error.set('');this.saves.activateLocal(this.leagueId(),memberId).pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:()=>{const page=location.hash.slice(1).split('/')[0];location.hash=`${page}/${this.leagueId()}/local-${memberId}`;},error:e=>this.error.set(e.error?.message||'Não foi possível trocar o treinador.')});}
 add():void{if(this.busy()||!this.teamId||this.managerName.trim().length<2)return;this.busy.set(true);this.error.set('');this.saves.addLocal(this.leagueId(),this.managerName.trim(),+this.teamId).pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.managerName='';this.teamId=0;this.notice.set('Treinador incluído. Selecione-o acima para preparar o time.');},error:e=>this.error.set(e.error?.message||'Não foi possível incluir o treinador.')});}
}
