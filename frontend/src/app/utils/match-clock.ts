export function matchClock(minute:number,period:number):string {const limit=period===1?45:period===3?105:period===4?120:90;return minute>limit?`${limit}+${minute-limit}\u2032`:`${minute}\u2032`;}
import type { MatchView } from '../services/matchday.service';

export function matchIsHalfTime(match:MatchView|null|undefined):boolean {
 if(!match||match.period!==2||match.minute!==45||!match.awaitingCoachDecision)return false;
 const interval=match.events.find(e=>e.type==='HALF_TIME');
 return !!interval&&!match.events.some(e=>e.sequence>interval.sequence&&e.type==='RESUMED');
}

export function scoreboardClock(match:MatchView|null|undefined):string {
 if(!match)return '';
 if(matchIsHalfTime(match)){
  const interval=match.events.find(e=>e.type==='HALF_TIME');
  return matchClock(interval?.minute??45+match.addedFirst,1);
 }
 return matchClock(match.minute,match.period);
}

export function announcedStoppageTime(match:MatchView|null|undefined):number|null {
 if(!match||match.status==='FINISHED')return null;
 if(match.period>=3)return match.minute>=(match.period===3?105:120)?1:null;
 if(matchIsHalfTime(match))return match.addedFirst;
 if(match.minute>=(match.period===1?45:90))return match.period===1?match.addedFirst:match.addedSecond;
 return null;
}

