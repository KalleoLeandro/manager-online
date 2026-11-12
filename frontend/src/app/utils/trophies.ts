/** Stable image IDs match backend/res/trophies; image requests always go to our backend. */
export interface Trophy { id:number; name:string }
export const PLAYER_AWARDS:readonly Trophy[]=[{id:40,name:'Bola de ouro'},{id:41,name:'Chuteira de ouro'},{id:42,name:'Luva de ouro'}];
export const WORLD_TROPHIES:readonly Trophy[]=[{id:32,name:'Copa Intercontinental da FIFA'},{id:33,name:'Mundial de Clubes da FIFA'}];
export function competitionTrophy(key:string,name:string):Trophy{
 const ids:Record<string,number>={BRA_CUP:20,ENG_CUP:21,ESP_CUP:22,ITA_CUP:23,FRA_CUP:24,GER_CUP:25,LIBERTADORES:30,CHAMPIONS:31,INTERCONTINENTAL:32,CLUB_WORLD_CUP:33};
 return {id:ids[key]||3,name};
}
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
