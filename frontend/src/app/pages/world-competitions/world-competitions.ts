import { Component, DestroyRef, effect, inject, input, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { WorldCompetitionsService, WorldCompetition, WorldChampionship } from '../../services/world-competitions.service';
import { ClubCrest } from '../../components/club-crest/club-crest';
import { dateLabel } from '../../utils/game-calendar';
@Component({selector:'app-world-competitions',imports:[FormsModule,ClubCrest],templateUrl:'./world-competitions.html',styleUrl:'./world-competitions.css'})
export class WorldCompetitionsPage {
 readonly leagueId=input.required<string>();readonly revision=input(0);
 private readonly api=inject(WorldCompetitionsService);private readonly destroy=inject(DestroyRef);private request?:Subscription;private overviewRequest?:Subscription;
 readonly competitions=signal<WorldCompetition[]>([]);readonly date=signal('');readonly country=signal('');readonly selected=signal<number|null>(null);readonly data=signal<WorldChampionship|null>(null);readonly error=signal('');readonly loading=signal(false);readonly label=dateLabel;
 readonly countries=computed(()=>[...new Set(this.competitions().map(c=>c.country))]);
 readonly visible=computed(()=>this.competitions().filter(c=>!this.country()||c.country===this.country()));
 constructor(){effect(()=>{this.leagueId();this.revision();this.load();});}
 load():void{this.overviewRequest?.unsubscribe();this.error.set('');this.overviewRequest=this.api.overview(this.leagueId()).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{this.competitions.set(r.competitions);this.date.set(r.date);const selection=this.visible().some(c=>c.id===this.selected())?this.selected():this.visible()[0]?.id;if(selection)this.select(selection);},error:e=>this.error.set(e.error?.message||'Não foi possível carregar as competições.')});}
 position(index:number):number {const table=this.data()?.table||[];return table.slice(0,index).filter(t=>t.groupName===table[index]?.groupName).length+1;}
 filter(country:string):void{this.country.set(country);const first=this.visible()[0];if(first)this.select(first.id);else{this.request?.unsubscribe();this.selected.set(null);this.data.set(null);this.loading.set(false);}}
 select(id:number):void{this.request?.unsubscribe();this.selected.set(Number(id));this.data.set(null);this.error.set('');this.loading.set(true);this.request=this.api.championship(this.leagueId(),Number(id)).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{this.data.set(r);this.loading.set(false);},error:e=>{this.error.set(e.error?.message||'Não foi possível carregar este campeonato.');this.loading.set(false);}});}
}
