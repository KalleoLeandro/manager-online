export interface PitchPosition {x:number;y:number}
export interface TacticalPlan {passingLength:number;passingRisk:number;tempo:number;width:number;defensiveLine:number;pressing:number;counterAttack:boolean;offsideTrap:boolean;positions:Record<number,PitchPosition>}
export interface PitchPlayer {id:number;name:string;position:string;ability:number;assignedPosition?:string;naturalPosition?:string;positions?:string[];preferredPositions?:string[];slot?:number;number?:string|null}
export function defaultTacticalPlan(plan?:Partial<TacticalPlan>|null):TacticalPlan{return {passingLength:50,passingRisk:50,tempo:50,width:50,defensiveLine:50,pressing:50,counterAttack:false,offsideTrap:false,...plan,positions:{...plan?.positions}};}
export function defaultPitchPositions(players:PitchPlayer[],formation:string):Record<number,PitchPosition>{
 const result:Record<number,PitchPosition>={};
 if(players.length===11||players.some(p=>p.slot!=null&&p.slot>=0)){let points:{x:number;y:number}[]=[{x:50,y:89}];const rows=formation.split('-').map(Number);rows.forEach((count,row)=>{for(let i=0;i<count;i++)points.push({x:100*(i+1)/(count+1),y:70-row*48/(rows.length-1)});});if(formation==='4-3-3')points=[{x:50,y:89},{x:15,y:65},{x:38,y:73},{x:62,y:73},{x:85,y:65},{x:50,y:56},{x:32,y:42},{x:68,y:42},{x:18,y:22},{x:50,y:15},{x:82,y:22}];players.forEach((p,i)=>result[p.id]=points[p.slot!=null&&p.slot>=0?p.slot:i]);}
 else {const groups=['GOL','DEF','MEI','ATA'];for(const role of groups){const line=players.filter(p=>p.position===role);line.forEach((p,i)=>result[p.id]={x:role==='GOL'?50:100*(i+1)/(line.length+1),y:role==='GOL'?89:role==='DEF'?70:role==='MEI'?46:22});}}return result;
}
