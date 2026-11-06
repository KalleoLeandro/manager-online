import {effectiveAbility} from './player-attributes';
export interface PositionedPlayer {position:string;ability:number;positions?:string[];preferredPositions?:string[];naturalPosition?:string;assignedPosition?:string;fitness?:number;technical?:{attributes:Record<string,number>}}
export function positionFamily(role:string):string{return role==='GOL'?'GOL':['DEF','ZAG','LE','LD','ALE','ALD'].includes(role)?'DEF':['ATA','PE','PD','CA','SA'].includes(role)?'ATA':'MEI';}
export function preferredPositions(p:PositionedPlayer):string[]{
 if(p.positions?.length)return p.positions;if(p.preferredPositions?.length)return p.preferredPositions;
 const natural=p.naturalPosition||p.position,a=p.technical?.attributes||{};
 if(!['GOL','DEF','MEI','ATA'].includes(natural))return [natural];
 if(natural==='GOL')return ['GOL'];
 if(natural==='DEF')return (a['pace']??50)>(a['tackling']??50)+4||(a['crossing']??50)>=(a['tackling']??50)-5?['LE','LD','ALE','ALD']:['ZAG'];
 if(natural==='MEI')return (a['tackling']??50)>=(a['short_passing']??50)-2?['VOL','MC']:['MC','MEI'];
 return (a['pace']??50)+(a['dribbling']??50)>(a['finishing']??50)*2+4?['PE','PD','SA']:['CA','SA'];
}
export function positionFit(p:PositionedPlayer,assigned:string):number{
 const natural=p.naturalPosition||p.position,preferred=preferredPositions(p);if(preferred.includes(assigned))return 1;
 const naturalFamily=positionFamily(natural),assignedFamily=positionFamily(assigned);
 if(naturalFamily==='GOL'||assignedFamily==='GOL')return .18;
 if(naturalFamily==='DEF'&&assignedFamily==='DEF'){
  if([['LE','ALE'],['LD','ALD']].some(side=>side.includes(assigned)&&preferred.some(r=>side.includes(r))))return .96;
  return .90;
 }
 if(naturalFamily==='ATA'&&assignedFamily==='ATA'){
  if([['PE','PD'],['CA','SA']].some(group=>group.includes(assigned)&&preferred.some(r=>group.includes(r))))return .96;
  return .90;
 }
 if(assignedFamily===naturalFamily)return .72;
 if([['ME','PE','ALE','LE'],['MD','PD','ALD','LD']].some(side=>side.includes(assigned)&&preferred.some(r=>side.includes(r))))return .75;
 return .42;
}
export function playingAbility(p:PositionedPlayer,role:string):number{return Math.max(1,Math.round(effectiveAbility(p)*positionFit(p,role)));}

export function roleAt(original:string,point:{x:number;y:number}):string{if(original==='GOL')return original;const {x,y}=point;if(y>=64)return x<26?'LE':x>74?'LD':'ZAG';if(y>=51)return x<25?'ALE':x>75?'ALD':'VOL';if(y>=31)return x<25?'ME':x>75?'MD':y>40?'MC':'MEI';return x<28?'PE':x>72?'PD':'CA';}
