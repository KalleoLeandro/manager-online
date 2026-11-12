import { PlayerProfile } from '../components/player-details/player-details';
import { MatchPlayer } from '../services/matchday.service';

export function matchPlayerProfile(base:PlayerProfile,live:MatchPlayer):PlayerProfile {
 const morale=live.morale??base.condition?.morale??50,sharpness=live.sharpness??base.condition?.sharpness??50;
 const performanceFactor=(.68+.32*live.fitness/100)*(.9+.1*morale/100)*(.9+.1*sharpness/100);
 const available=!live.injured&&!live.sentOff&&live.fitness>=40;
 return {...base,ability:live.ability,position:live.naturalPosition||base.position,positions:live.preferredPositions?.length?live.preferredPositions:base.positions,fitness:live.fitness,
  condition:{fitness:live.fitness,effectiveAbility:available?Math.max(1,Math.round(live.ability*performanceFactor)):0,available,
   status:live.injured?'INJURED':live.sentOff?'SUSPENDED':live.fitness<40?'UNFIT':live.fitness<65?'TIRED':'AVAILABLE',
   injuryName:live.injured?'Lesão durante a partida':null,returnRound:null,morale,sharpness,performanceFactor}};
}
