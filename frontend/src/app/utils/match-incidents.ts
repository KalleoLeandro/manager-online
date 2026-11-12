import type { MatchEvent, MatchView } from '../services/matchday.service';

export interface MatchIncident {
 key:string;fixtureId:number;sequence:number;type:'YELLOW'|'RED'|'INJURY';
 title:string;description:string;playerId:number|null;playerName:string;teamId:number;teamName:string;minute:number;period:number;
}

/** Track each fixture separately so polling and switching games never replay old incidents. */
export class MatchIncidentTracker {
 private readonly seen=new Map<number,number>();
 collect(match:MatchView):MatchIncident[]{
  const previous=this.seen.get(match.fixtureId);
  const latest=Math.max(0,...match.events.map(e=>e.sequence));
  if(previous!==undefined&&latest<previous)return [];
  this.seen.set(match.fixtureId,latest);
  if(match.status==='FINISHED'&&previous===undefined)return [];
  const fresh=match.events.filter(e=>previous===undefined?e.minute===match.minute&&e.period===match.period:e.sequence>previous);
  return fresh.filter(e=>['YELLOW','RED','INJURY'].includes(e.type))
   // A second yellow and its red are one incident, not two consecutive dialogs.
   .filter(e=>e.type!=='YELLOW'||!fresh.some(r=>r.type==='RED'&&r.playerId===e.playerId&&r.teamId===e.teamId&&r.minute===e.minute))
   .map(e=>this.details(match,e));
 }
 private details(match:MatchView,event:MatchEvent):MatchIncident {
  const team=event.teamId===match.home.id?match.home:match.away;
  const player=team.players.find(p=>p.id===event.playerId);
  const foul=[...match.events].reverse().find(e=>e.type==='FOUL'&&e.minute===event.minute&&e.period===event.period&&e.sequence<event.sequence&&(e.playerId===event.playerId||e.relatedPlayerId===event.playerId));
  const detail=event.type==='INJURY'?'O atleta deixou o campo e recebeu atendimento médico.':event.type==='RED'?'O jogador foi expulso e a equipe terá de continuar com um atleta a menos.':'O árbitro advertiu o jogador com cartão amarelo.';
  return {key:`${match.fixtureId}:${event.sequence}`,fixtureId:match.fixtureId,sequence:event.sequence,type:event.type as MatchIncident['type'],title:event.type==='INJURY'?'Lesão':event.type==='RED'?'Cartão vermelho':'Cartão amarelo',description:[foul?.text,event.text,detail].filter(Boolean).join(' '),playerId:event.playerId,playerName:player?.name||team.name,teamId:team.id,teamName:team.name,minute:event.minute,period:event.period};
 }
}
