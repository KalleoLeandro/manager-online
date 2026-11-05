import { Component, computed, input } from '@angular/core';
import { MatchStats } from '../../services/matchday.service';
@Component({selector:'app-match-statistics',templateUrl:'./match-statistics.html',styleUrl:'./match-statistics.css'})
export class MatchStatistics {
 readonly home=input.required<MatchStats>();readonly away=input.required<MatchStats>();
 readonly rows=computed(()=>{const h=this.home(),a=this.away(),total=h.possessionTicks+a.possessionTicks;return [
  ['Posse de bola',total?Math.round(100*h.possessionTicks/total)+'%':'50%',total?Math.round(100*a.possessionTicks/total)+'%':'50%'],
  ['Finalizações',h.shots,a.shots],['No alvo',h.shotsOnTarget,a.shotsOnTarget],['Gols esperados (xG)',h.expectedGoals.toFixed(2),a.expectedGoals.toFixed(2)],
  ['Passes certos',h.passesCompleted,a.passesCompleted],['Passes errados',h.passesMissed,a.passesMissed],
  ['Aproveitamento dos passes',this.accuracy(h),this.accuracy(a)],['Defesas',h.saves,a.saves],['Escanteios',h.corners,a.corners],['Laterais',h.throwIns,a.throwIns],
  ['Desarmes',h.tackles,a.tackles],['Interceptações',h.interceptions,a.interceptions],['Chutes bloqueados',h.blockedShots,a.blockedShots],['Faltas',h.fouls,a.fouls],['Impedimentos',h.offsides,a.offsides],['Amarelos',h.yellows,a.yellows],['Vermelhos',h.reds,a.reds],['Lesões',h.injuries,a.injuries],['Pênaltis',h.penalties,a.penalties]
 ];});
 private accuracy(s:MatchStats){const total=s.passesCompleted+s.passesMissed;return total?Math.round(s.passesCompleted*100/total)+'%':'—';}
}
