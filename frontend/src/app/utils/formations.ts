import {available,effectiveAbility} from './player-attributes';
import { ManagedPlayer } from '../services/management.service';
import {positionFit} from './player-positions';
export const formationRoles:Record<string,string[]>={
'4-3-3':['GOL','LE','ZAG','ZAG','LD','VOL','MC','MC','PE','CA','PD'],
'4-4-2':['GOL','LE','ZAG','ZAG','LD','ME','MC','MC','MD','CA','CA'],
'3-5-2':['GOL','ZAG','ZAG','ZAG','ALE','VOL','MC','MEI','ALD','CA','CA'],
'4-5-1':['GOL','LE','ZAG','ZAG','LD','ME','VOL','MC','MC','MD','CA'],
'4-2-3-1':['GOL','LE','ZAG','ZAG','LD','VOL','VOL','PE','MEI','PD','CA'],
'4-1-4-1':['GOL','LE','ZAG','ZAG','LD','VOL','ME','MC','MC','MD','CA'],
'4-3-1-2':['GOL','LE','ZAG','ZAG','LD','VOL','MC','MC','MEI','CA','CA'],
'4-1-2-1-2':['GOL','LE','ZAG','ZAG','LD','VOL','MC','MC','MEI','CA','CA'],
'3-4-3':['GOL','ZAG','ZAG','ZAG','ALE','MC','MC','ALD','PE','CA','PD'],
'3-4-2-1':['GOL','ZAG','ZAG','ZAG','ALE','MC','MC','ALD','MEI','MEI','CA'],
'5-3-2':['GOL','ALE','ZAG','ZAG','ZAG','ALD','VOL','MC','MC','CA','CA'],
'5-4-1':['GOL','ALE','ZAG','ZAG','ZAG','ALD','ME','MC','MC','MD','CA']};
export const formations:Record<string,number[]>=Object.fromEntries(Object.keys(formationRoles).map(f=>[f,f.split('-').map(Number)]));
export function roles(formation:string):string[]{return formationRoles[formation]||formationRoles['4-3-3'];}
export function suggestLineup(players:ManagedPlayer[],formation:string):number[]{const selected=new Set<number>();return roles(formation).map(role=>{const player=players.filter(p=>available(p)&&!selected.has(p.id)).sort((a,b)=>effectiveAbility(b)*positionFit(b,role)-effectiveAbility(a)*positionFit(a,role))[0];if(player)selected.add(player.id);return player?.id||0;});}
