/** Optional offline import of verified final European tables. Downloaded data stays outside Git. */
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');process.chdir(root);
const directory=path.join(root,'.tools/season-history');fs.mkdirSync(directory,{recursive:true});
const apply=process.argv.includes('--apply');
const profiles=[['eng.1',20,'Liverpool',84],['esp.1',20,'Barcelona',88],['ita.1',20,'Napoli',82],['fra.1',18,'Paris Saint-Germain',84],['ger.1',18,'Bayern Munich',82]];
const quote=v=>"'"+String(v).replaceAll("'","''")+"'";
function psql(sql){const r=spawnSync('docker',['compose','exec','-T','postgres','sh','-c','psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" "$POSTGRES_DB"'],{input:sql,encoding:'utf8',windowsHide:true,maxBuffer:16*1024*1024});if(r.status!==0)throw Error(r.stderr||'Database unavailable');return r.stdout;}
const clubIds=new Map(psql('SELECT source_id,id FROM catalog_teams WHERE source_id IS NOT NULL;').trim().split(/\r?\n/).map(s=>s.split('|')));
const leagueIds=new Map(psql('SELECT source_id,id FROM catalog_leagues WHERE source_id IS NOT NULL;').trim().split(/\r?\n/).map(s=>s.split('|')));
const sql=['BEGIN;'];const report=[];
for(const [code,count,champion,points] of profiles){
 const source=`https://site.api.espn.com/apis/v2/sports/soccer/${code}/standings?season=2024`;
 const filename=path.join(directory,code+'-2024.json');let data;
 if(fs.existsSync(filename))data=JSON.parse(fs.readFileSync(filename,'utf8'));
 else{const r=await fetch(source,{signal:AbortSignal.timeout(20000)});if(!r.ok)throw Error('Historical feed HTTP '+r.status);data=await r.json();fs.writeFileSync(filename,JSON.stringify(data));}
 if(data.season?.year!==2024)throw Error('Unexpected historical season: '+code);
 const entries=data.children?.[0]?.standings?.entries??data.standings?.entries;
 const stat=(entry,key)=>entry.stats.find(s=>s.name===key)?.value;
 if(entries?.length!==count||new Set(entries.map(e=>e.team.id)).size!==count)throw Error('Incomplete historical table: '+code);
 entries.sort((a,b)=>stat(a,'rank')-stat(b,'rank'));
 if(entries[0].team.displayName!==champion||stat(entries[0],'points')!==points)throw Error('Champion verification failed: '+code);
 const ranks=new Set();
 for(const entry of entries){
  if(!clubIds.has(String(entry.team.id)))throw Error('Import this real historical club first: '+entry.team.displayName+' (ESPN '+entry.team.id+')');
  for(const key of ['rank','gamesPlayed','wins','ties','losses','pointsFor','pointsAgainst','points'])if(!Number.isInteger(stat(entry,key))||stat(entry,key)<0)throw Error('Invalid historical statistic: '+code+' '+key);
  if(stat(entry,'gamesPlayed')!==(count-1)*2||stat(entry,'wins')+stat(entry,'ties')+stat(entry,'losses')!==stat(entry,'gamesPlayed'))throw Error('Historical game count mismatch: '+code);
  ranks.add(stat(entry,'rank'));
 }
 if(ranks.size!==count||Math.max(...ranks)!==count||Math.min(...ranks)!==1)throw Error('Invalid historical ranks');
 if(entries.reduce((n,e)=>n+stat(e,'pointsFor')-stat(e,'pointsAgainst'),0)!==0)throw Error('Historical goal totals mismatch');
 const league=leagueIds.get(code);if(!/^\d+$/.test(league??''))throw Error('Missing league '+code);
 sql.push(`INSERT INTO historical_seasons(league_id,season_year,label,name,country,first_date,last_date,source_url) SELECT id,2024,'2024/2025',name,country,DATE '2024-08-01',DATE '2025-05-31',${quote(source)} FROM catalog_leagues WHERE id=${league} ON CONFLICT(league_id,season_year) DO NOTHING;`);
 for(const e of entries){const values=['rank','gamesPlayed','wins','ties','losses','pointsFor','pointsAgainst','points'].map(k=>stat(e,k)).join(',');sql.push(`INSERT INTO historical_standings(season_id,team_id,team_name,position,played,wins,draws,losses,goals_for,goals_against,points) SELECT id,${clubIds.get(String(e.team.id))},${quote(e.team.displayName)},${values} FROM historical_seasons WHERE league_id=${league} AND season_year=2024 ON CONFLICT(season_id,team_id) DO NOTHING;`);}
 report.push({league:code,teams:count,champion,points,source});
}
sql.push('COMMIT;');fs.writeFileSync(path.join(directory,'import.sql'),sql.join('\n'));fs.writeFileSync(path.join(directory,'report.json'),JSON.stringify(report,null,2));
if(apply)psql(sql.join('\n'));
console.log(JSON.stringify({applied:apply,seasons:report.length,clubs:report.reduce((n,c)=>n+c.teams,0),report:'.tools/season-history/report.json'}));
