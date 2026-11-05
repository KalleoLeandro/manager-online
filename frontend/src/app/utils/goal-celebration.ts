import { MatchView } from '../services/matchday.service';

export interface GoalCelebration {
 key:string;playerId:number;playerName:string;teamId:number;teamName:string;goals:number;
 assistName:string|null;minute:number;period:number;remainingMillis:number;sequence:number;
}
export function currentGoal(match:MatchView|null|undefined):GoalCelebration|null {
 if(!match||match.varReview||!(match.goalCelebrationRemainingMillis>0))return null;
 const event=match.events.find(e=>e.sequence===match.celebratingGoalSequence&&e.type==='GOAL');
 if(!event||event.playerId==null)return null;
 const team=event.teamId===match.home.id?match.home:match.away;
 const scorer=team.players.find(p=>p.id===event.playerId);if(!scorer)return null;
 const assistant=event.relatedPlayerId==null?null:team.players.find(p=>p.id===event.relatedPlayerId);
 return {key:`${match.fixtureId}:${event.sequence}`,playerId:scorer.id,playerName:scorer.name,teamId:team.id,teamName:team.name,
  goals:match.events.filter(e=>e.type==='GOAL'&&e.playerId===scorer.id&&e.sequence<=event.sequence).length,
  assistName:assistant?.name||null,minute:event.minute,period:event.period,remainingMillis:Math.min(5000,match.goalCelebrationRemainingMillis),sequence:event.sequence};
}
