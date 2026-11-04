import { Component, computed, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ManagementService } from '../../services/management.service';
import { TeamRoster } from '../../components/team-roster/team-roster';
import { Player } from '../../services/catalog.service';

@Component({selector:'app-junior-team',imports:[TeamRoster],templateUrl:'./junior-team.html',styleUrl:'./junior-team.css'})
export class JuniorTeamPage implements OnInit {
  readonly leagueId=input.required<string>();
  readonly teamName=input.required<string>();
  private readonly api=inject(ManagementService);
  private readonly destroy=inject(DestroyRef);
  readonly players=signal<Player[]>([]);
  readonly prospects=computed(()=>this.players().filter(p=>p.level==='Promessa'||p.level==='Joia da base'));
  readonly error=signal('');
  readonly loading=signal(true);
  ngOnInit():void{this.load();}
  load():void{this.loading.set(true);this.error.set('');this.api.juniors(this.leagueId()).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:players=>{this.players.set(players.map(p=>({...p,nationality:p.nationality??null})));this.loading.set(false);},error:e=>{this.error.set(e.error?.message||'Não foi possível carregar os juniores.');this.loading.set(false);}});}
}
