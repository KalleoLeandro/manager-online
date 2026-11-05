/** Stable image IDs match backend/res/trophies; image requests always go to our backend. */
export interface Trophy { id:number; name:string }
export const PLAYER_AWARDS:readonly Trophy[]=[{id:40,name:'Bola de ouro'},{id:41,name:'Chuteira de ouro'},{id:42,name:'Luva de ouro'}];
const competitions:Record<string,[Trophy,Trophy]>={
 brazil:[{id:10,name:'Brasileirão'},{id:20,name:'Copa do Brasil'}],
 england:[{id:11,name:'Premier League'},{id:21,name:'FA Cup'}],
 spain:[{id:12,name:'La Liga'},{id:22,name:'Copa del Rey'}],
 italy:[{id:13,name:'Serie A'},{id:23,name:'Coppa Italia'}],
 france:[{id:14,name:'Ligue 1'},{id:24,name:'Coupe de France'}],
 germany:[{id:15,name:'Bundesliga'},{id:25,name:'DFB-Pokal'}]
};
const aliases:Record<string,string>={brasil:'brazil',inglaterra:'england',espanha:'spain',italia:'italy',franca:'france',alemanha:'germany'};
export function seasonTrophies(country:string,division:number,competitionName:string):{national:Trophy;cup:Trophy;international:Trophy}{
 const normalized=(country||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
 const key=aliases[normalized]||normalized;
 const [national,cup]=competitions[key]||[{id:1,name:'Campeonato nacional'},{id:2,name:'Copa do país'}];
 return {national:{id:division===1?national.id:1,name:competitionName||national.name},cup,international:key==='brazil'?{id:30,name:'CONMEBOL Libertadores'}:{id:31,name:'UEFA Champions League'}};
}
