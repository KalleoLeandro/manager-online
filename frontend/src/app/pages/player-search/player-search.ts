import { Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ManagementService, ManagedPlayer } from '../../services/management.service';
import { PlayerDetails } from '../../components/player-details/player-details';
import { PlayerPortrait } from '../../components/player-portrait/player-portrait';

interface Result {player:ManagedPlayer;teamId:number;teamName:string}
@Component({selector:'app-player-search',imports:[FormsModule,PlayerDetails,PlayerPortrait],templateUrl:'./player-search.html',styleUrl:'./player-search.css'})
export class PlayerSearchPage {
  readonly leagueId=input.required<string>();readonly negotiate=output<number>();
  private readonly api=inject(ManagementService);
  private readonly destroy=inject(DestroyRef);
  readonly query=signal('');readonly results=signal<Result[]>([]);readonly selected=signal<Result|null>(null);
  readonly loading=signal(false);readonly searched=signal(false);readonly error=signal('');
  search():void{if(this.loading()||this.query().trim().length<2)return;this.loading.set(true);this.error.set('');this.api.search(this.leagueId(),this.query().trim()).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:results=>{this.results.set(results);this.loading.set(false);this.searched.set(true);},error:e=>{this.error.set(e.error?.message||'Não foi possível buscar jogadores.');this.loading.set(false);}});}
}
