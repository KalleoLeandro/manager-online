/** Offline importer. Runtime never contacts ESPN. No existing roster or save is overwritten. */
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
process.chdir(root);
const cache = path.join(root, '.tools/world-catalog');
fs.mkdirSync(cache, { recursive: true });
const apply = process.argv.includes('--apply');
const assetsOnly = process.argv.includes('--assets-only');
const season = Number(process.argv.find(a => a.startsWith('--season='))?.split('=')[1] ?? 2026);
if (!Number.isInteger(season) || season < 2020 || season > 2100) throw Error('Invalid season');
const feeds = ['bra.copa_do_brazil', 'eng.fa', 'esp.copa_del_rey', 'ita.coppa_italia', 'ger.dfb_pokal', 'fra.coupe_de_france', 'uefa.champions', 'conmebol.libertadores', 'bol.1', 'per.1', 'ecu.1'];
const countries = { bra: 'Brasil', eng: 'Inglaterra', esp: 'Espanha', ita: 'It\u00e1lia', ger: 'Alemanha', fra: 'Fran\u00e7a', por: 'Portugal', gre: 'Gr\u00e9cia', aut: '\u00c1ustria', bel: 'B\u00e9lgica', cze: 'Rep\u00fablica Tcheca', nor: 'Noruega', ned: 'Pa\u00edses Baixos', tur: 'Turquia', ukr: 'Ucr\u00e2nia', svk: 'Eslov\u00e1quia', aze: 'Azerbaij\u00e3o', arg: 'Argentina', bol: 'Bol\u00edvia', chi: 'Chile', col: 'Col\u00f4mbia', ecu: 'Equador', par: 'Paraguai', per: 'Peru', uru: 'Uruguai', ven: 'Venezuela' };
const quote = v => v == null ? 'NULL' : "'" + String(v).replaceAll("'", "''") + "'";
function psql(sql) {
  const result = spawnSync('docker', ['compose', 'exec', '-T', 'postgres', 'sh', '-c', 'psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" "$POSTGRES_DB"'], { input: sql, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, windowsHide: true });
  if (result.status !== 0) throw Error(result.stderr || result.error?.message || 'PostgreSQL unavailable');
  return result.stdout;
}
async function json(url, filename) {
  const target = path.join(cache, filename);
  if (fs.existsSync(target)) return JSON.parse(fs.readFileSync(target, 'utf8'));
  const response = await fetch(url, { signal: AbortSignal.timeout(20000) });
  if (!response.ok) throw Error(`HTTP ${response.status}: ${url}`);
  const data = await response.json();
  fs.writeFileSync(target, JSON.stringify(data));
  return data;
}
const existing = new Set(psql('SELECT source_id FROM catalog_teams WHERE source_id IS NOT NULL;').trim().split(/\r?\n/));
const existingPlayers = new Set(psql('SELECT source_id FROM catalog_players WHERE source_id IS NOT NULL;').trim().split(/\r?\n/));
const countryOverrides = { 'Bodo/Glimt': countries.nor, 'Viking FK': countries.nor, 'Sabah FK': countries.aze, 'Slavia Prague': countries.cze, 'Always Ready': countries.bol, 'Deportivo La Guaira': countries.ven, Platense: countries.arg };
const all = new Map();
const failures = [];
for (const feed of assetsOnly ? [] : feeds) {
  const url = `https://site.api.espn.com/apis/site/v2/sports/soccer/${feed}/teams?limit=1000&season=${season}`;
  try {
    const data = await json(url, `teams-${feed}-${season}.json`);
    for (const entry of data.sports?.[0]?.leagues?.[0]?.teams ?? []) {
      const t = entry.team;
      if (existing.has(String(t.id)) || all.has(String(t.id))) continue;
      const country = countries[feed.split('.')[0]] ?? countries[t.slug?.split('.')[0]] ?? countryOverrides[t.displayName];
      if (!country) { failures.push({ club: t.displayName, reason: 'Association not identified; manual mapping required' }); continue; }
      all.set(String(t.id), { ...t, country, feed, source: url });
    }
  } catch (error) { failures.push({ feed, reason: error.message }); }
}
const pools = new Map();
function namePool(country) {
  if (!pools.has(country)) {
    const result = spawnSync('java', ['-Dfile.encoding=UTF-8', '-cp', path.join(root, 'backend/target/classes'), 'br.com.manager.utils.PlayerNameGenerator', country, '5000', '2026'], { encoding: 'utf8', maxBuffer: 4 * 1024 * 1024, windowsHide: true });
    if (result.status !== 0) throw Error('Compile the backend first. ' + (result.stderr ?? ''));
    pools.set(country, result.stdout.trim().split(/\r?\n/));
  }
  return pools.get(country);
}
function attribute(seed, index, base) { return Math.max(20, Math.min(90, base + ((seed * 31 + index * 17) % 15) - 7)); }
const clubs = [...all.values()];
let cursor = 0;
await Promise.all(Array.from({ length: 4 }, async () => {
  while (cursor < clubs.length) {
    const t = clubs[cursor++];
    try {
      const roster = await json(`https://site.api.espn.com/apis/site/v2/sports/soccer/${t.feed}/teams/${t.id}/roster?season=${season}`, `roster-${t.id}-${season}.json`);
      t.roster = Array.isArray(roster.athletes) ? roster.athletes : [];
      // A valid empty response is distinguishable from a network failure.
    } catch (error) { t.rosterError = error.message; failures.push({ club: t.displayName, reason: error.message }); }
  }
}));
const statements = ['BEGIN;'];
const report = { season, clubs: [], failures };
for (const t of clubs) {
  // A transient timeout must never be interpreted as proof that a real roster does not exist.
  if (t.rosterError) continue;
  const source = String(t.id), country = quote(t.country), leagueSource = quote(`world-support:${t.country}`);
  const base = t.feed === 'uefa.champions' ? 74 : t.feed === 'conmebol.libertadores' ? 70 : 58;
  const color = /^[0-9a-f]{6}$/i.test(t.color ?? '') ? '#' + t.color : '#355e4b';
  statements.push(`INSERT INTO catalog_leagues(name,country,selectable,season,team_count,division,source_id,source_url) VALUES('Clubes complementares',${country},FALSE,${quote(String(season))},0,9,${leagueSource},${quote(t.source)}) ON CONFLICT(source_id) DO NOTHING;`);
  statements.push(`INSERT INTO catalog_teams(league_id,name,short_name,abbreviation,crest_url,color,rating,national_rank,international_bonus,selectable,source_id,source_url) SELECT id,${quote(t.displayName)},${quote(t.shortDisplayName ?? t.displayName)},${quote((t.abbreviation ?? t.displayName.slice(0,3)).slice(0,20))},NULL,${quote(color)},${base},999,0,FALSE,${quote(source)},${quote(t.source)} FROM catalog_leagues WHERE source_id=${leagueSource} ON CONFLICT(source_id) DO NOTHING;`);
  const seen = new Set(), reserved = new Set();
  const players = [];
  for (const p of t.roster) {
    if (!p.id || !p.fullName || seen.has(String(p.id)) || existingPlayers.has(String(p.id))) continue;
    seen.add(String(p.id)); reserved.add(p.fullName);
    const pos = ({ G: 'GOL', GK: 'GOL', D: 'DEF', M: 'MEI', F: 'ATA' })[p.position?.abbreviation];
    if (!pos) continue;
    players.push({ source: String(p.id), name: p.fullName, pos, age: p.age ?? null, shirt: p.jersey ?? null, nationality: p.citizenship ?? t.country, generated: false });
    existingPlayers.add(String(p.id));
  }
  const real = players.length;
  const roles = ['GOL','GOL',...Array(7).fill('DEF'),...Array(7).fill('MEI'),...Array(7).fill('ATA')];
  for (let i = players.length; i < 23; i++) {
    let name;
    do { name = namePool(t.country).shift(); if (!name) throw Error('Name pool exhausted'); } while (reserved.has(name));
    reserved.add(name);
    players.push({ source: `world-generated:${source}:${i}`, name, pos: roles[i], age: 18 + (Number(t.id) + i * 7) % 17, shirt: String(i + 1), nationality: t.country, generated: true });
  }
  for (let i = 0; i < players.length; i++) {
    const p = players[i], ability = attribute(Number(t.id), i, base);
    const attrs = p.pos === 'GOL' ? ['Reflexos','Posicionamento'] : p.pos === 'DEF' ? ['Desarme','For\u00e7a'] : p.pos === 'MEI' ? ['Passe','Drible'] : ['Finaliza\u00e7\u00e3o','Velocidade'];
    statements.push(`INSERT INTO catalog_players(team_id,name,position,level,ability,attribute1,value1,attribute2,value2,age,shirt_number,photo_url,nationality,appearances,goals,assists,minutes,source_url,source_id,generated,generation_origin) SELECT id,${quote(p.name)},${quote(p.pos)},'Regular',${ability},${quote(attrs[0])},${ability},${quote(attrs[1])},${attribute(Number(t.id),i+1,base)},${p.age == null ? 'NULL' : Number(p.age)},${quote(p.shirt)},NULL,${quote(p.nationality)},0,0,0,0,${quote(t.source)},${quote(p.source)},${p.generated ? 'TRUE' : 'FALSE'},${p.generated ? "'WORLD_ROSTER_GAP'" : 'NULL'} FROM catalog_teams WHERE source_id=${quote(source)} ON CONFLICT(source_id) DO NOTHING;`);
  }
  report.clubs.push({ source, name: t.displayName, country: t.country, realPlayers: real, generatedPlayers: players.length-real });
}
statements.push("UPDATE catalog_teams SET crest_url='/api/teams/'||id||'/crest' WHERE source_id IN (" + (report.clubs.map(t=>quote(t.source)).join(',') || 'NULL') + ') AND crest_url IS NULL;');
statements.push("UPDATE catalog_players SET photo_url='/api/players/'||id||'/portrait' WHERE team_id IN (SELECT id FROM catalog_teams WHERE source_id IN (" + (report.clubs.map(t=>quote(t.source)).join(',') || 'NULL') + ')) AND photo_url IS NULL;');
statements.push("UPDATE catalog_leagues l SET team_count=(SELECT COUNT(*) FROM catalog_teams t WHERE t.league_id=l.id) WHERE l.source_id LIKE 'world-support:%';", 'COMMIT;');
const sql = statements.join('\n');
if (!assetsOnly) fs.writeFileSync(path.join(cache, 'import.sql'), sql);
const reportName = assetsOnly ? 'assets-report.json' : 'report.json';
if (!assetsOnly) fs.writeFileSync(path.join(cache, reportName), JSON.stringify(report, null, 2));
if (apply || assetsOnly) {
  if (apply) psql(sql);
  const ids = psql('SELECT id,source_id FROM catalog_teams WHERE source_id IS NOT NULL;').trim().split(/\r?\n/);
  const directory = path.join(root, 'backend/src/main/resources/res/crests');
  fs.mkdirSync(directory, { recursive: true });
  let nextAsset = 0;
  await Promise.all(Array.from({length: 8}, async () => {
  while (nextAsset < ids.length) {
    const row = ids[nextAsset++];
    const [id, source] = row.split('|');
    if (!/^\d+$/.test(id ?? '') || !/^\d+$/.test(source ?? '')) continue;
    const target = path.join(directory, id + '.png');
    if (fs.existsSync(target)) continue;
    try {
      const response = await fetch(`https://a.espncdn.com/i/teamlogos/soccer/500/${source}.png`, { signal: AbortSignal.timeout(10000) });
      if (!response.ok) throw Error('Crest HTTP ' + response.status);
      const bytes = Buffer.from(await response.arrayBuffer());
      if (!bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) throw Error('Invalid PNG');
      fs.writeFileSync(target, bytes);
    } catch (error) { failures.push({ club: source, reason: error.message }); }
  }
  }));
  fs.writeFileSync(path.join(cache, reportName), JSON.stringify(report, null, 2));
}
console.log(JSON.stringify({ applied: apply, clubs: report.clubs.length, realPlayers: report.clubs.reduce((n,t)=>n+t.realPlayers,0), generatedPlayers: report.clubs.reduce((n,t)=>n+t.generatedPlayers,0), failures: failures.length, report: '.tools/world-catalog/' + reportName }));
