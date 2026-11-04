-- Synthetic integration fixtures. No real player data. Never packaged in the application.
INSERT INTO catalog_leagues (id,source_id,name,country,selectable,season,team_count,division,source_url,display_order) VALUES
(1,'eng.1','Premier League','Inglaterra',TRUE,'2026-27 English Premier League',20,1,NULL,0),
(2,'esp.1','LaLiga','Espanha',TRUE,'2026-27 Spanish LALIGA',20,1,NULL,1),
(3,'ita.1','Serie A','Itália',TRUE,'2026-27 Italian Serie A',20,1,NULL,2),
(4,'fra.1','Ligue 1','França',TRUE,'2026-27 French Ligue 1',18,1,NULL,3),
(5,'ger.1','Bundesliga','Alemanha',TRUE,'2026-27 German Bundesliga',18,1,NULL,4),
(6,'bra.1','Brasileirão','Brasil',TRUE,'2026 Futebol Brasileiro',20,1,NULL,5),
(7,'por.1','Primeira Liga','Portugal',FALSE,'2026-27 Portuguese Primeira Liga',5,1,NULL,6),
(8,'ned.1','Eredivisie','Países Baixos',FALSE,'2026-27 Dutch Eredivisie',5,1,NULL,7),
(9,'arg.1','Liga Profesional','Argentina',FALSE,'2026 Argentine Liga Profesional de Fútbol',5,1,NULL,8),
(10,'mex.1','Liga MX','México',FALSE,'2026-27 Liga BBVA MX',5,1,NULL,9),
(11,'usa.1','MLS','Estados Unidos',FALSE,'2026 MLS',5,1,NULL,10),
(12,'ksa.1','Saudi Pro League','Arábia Saudita',FALSE,'2026-27 Saudi Pro League',5,1,NULL,11),
(13,'bel.1','Pro League','Bélgica',FALSE,'2026-27 Belgian Pro League',5,1,NULL,12),
(14,'tur.1','Süper Lig','Turquia',FALSE,'2026-27 Turkish Super Lig',5,1,NULL,13),
(15,'sco.1','Premiership','Escócia',FALSE,'2026-27 Scottish Premiership',5,1,NULL,14),
(16,'jpn.1','J1 League','Japão',FALSE,'2026-27 Japanese J1 League',5,1,NULL,15),
(17,'col.1','Primera A','Colômbia',FALSE,'2026 Liga BetPlay',5,1,NULL,16),
(18,'uru.1','Primera División','Uruguai',FALSE,'2026 Liga AUF Uruguaya',5,1,NULL,17),
(19,'ecu.1','LigaPro','Equador',FALSE,'2026 LigaPro Ecuador',5,1,NULL,18),
(20,'chi.1','Primera División','Chile',FALSE,'2026 Primera División de Chile',5,1,NULL,19),
(21,'par.1','División Profesional','Paraguai',FALSE,'2026 Primera División de Paraguay',5,1,NULL,20),
(22,'eng.2','Championship','Inglaterra',TRUE,'2026/27',24,2,'https://example.invalid/test',21),
(23,'esp.2','LaLiga 2','Espanha',TRUE,'2026/27',22,2,'https://example.invalid/test',22),
(24,'ita.2','Serie B','Itália',TRUE,'2026/27',20,2,'https://example.invalid/test',23),
(25,'fra.2','Ligue 2','França',TRUE,'2026/27',18,2,'https://example.invalid/test',24),
(26,'ger.2','2. Bundesliga','Alemanha',TRUE,'2026/27',18,2,'https://example.invalid/test',25),
(27,'bra.2','Série B','Brasil',TRUE,'2026',20,2,'https://example.invalid/test',26),
(28,'eng.3','League One','Inglaterra',TRUE,'2026/27',24,3,'https://example.invalid/test',27),
(29,'ita.3','Serie C','Itália',TRUE,'2026/27',60,3,'https://example.invalid/test',28),
(30,'fra.3','Ligue 3','França',TRUE,'2026/27',18,3,'https://example.invalid/test',29),
(31,'bra.3','Série C','Brasil',TRUE,'2026',20,3,'https://example.invalid/test',30),
(32,'ger.3','3. Liga','Alemanha',TRUE,'2026/27',20,3,'https://example.invalid/test',31),
(33,'esp.3','Primera Federación','Espanha',TRUE,'2026/27',40,3,'https://example.invalid/test',32);

INSERT INTO catalog_teams (league_id,name,short_name,abbreviation,rating,national_rank,international_bonus,selectable,display_order)
SELECT l.id,'Clube teste ' || l.id || '-' || x,'Teste ' || l.id || '-' || x,'TST',70,CAST(x AS INTEGER),0,l.selectable,CAST(l.id*100+x AS INTEGER)
FROM catalog_leagues l CROSS JOIN SYSTEM_RANGE(1,60) WHERE x<=l.team_count ORDER BY l.id,x;
INSERT INTO catalog_players (team_id,name,position,level,ability,attribute1,value1,attribute2,value2,age,appearances,goals,assists,minutes)
SELECT t.id,'Atleta fictício ' || t.id || '-' || x,CASE WHEN x<=2 THEN 'GOL' WHEN x<=9 THEN 'DEF' WHEN x<=17 THEN 'MEI' ELSE 'ATA' END,'Titular',60+MOD(x,15),'Drible',65,'Finalização',65,24,0,0,0,0
FROM catalog_teams t CROSS JOIN SYSTEM_RANGE(1,24) ORDER BY t.id,x;
