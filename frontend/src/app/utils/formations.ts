import {available,effectiveAbility} from './player-attributes';
import { ManagedPlayer } from '../services/management.service';
export const formations: Record<string,number[]> = { '4-3-3':[4,3,3], '4-4-2':[4,4,2], '3-5-2':[3,5,2], '4-5-1':[4,5,1] };
export function roles(formation: string): string[] {
  const [def,mid,att]=formations[formation];
  return ['GOL',...Array(def).fill('DEF'),...Array(mid).fill('MEI'),...Array(att).fill('ATA')];
}
export function suggestLineup(players: ManagedPlayer[],formation: string): number[] {
  const selected=new Set<number>();
  return roles(formation).map(role => {
    const player=players.filter(p=>p.position===role&&available(p)&&!selected.has(p.id)).sort((a,b)=>effectiveAbility(b)-effectiveAbility(a))[0];
    if(player) selected.add(player.id);
    return player?.id || 0;
  });
}
