INSERT INTO catalog_roster_policies (id,source_key,country,season,reference,window_start,window_end,source_url,verification,note) VALUES
(1,'policy-2c5a9eb7294fb222e27f0662','Brasil','2026','POST_MID_YEAR_WINDOW','2026-07-20','2026-09-11','https://www.cbf.com.br/a-cbf/drt/janela-de-transferencia','CURRENT_PUBLIC_ROSTERS_NOT_HISTORICAL_SNAPSHOT','Brasil 2026: elencos de referência após a janela de meio de ano. A temporada começa normalmente no início do ano. As fontes públicas atuais não comprovam um retrato histórico exato de 11/09/2026.');
INSERT INTO catalog_roster_policy_leagues (policy_id,league_id) VALUES
(1,6),
(1,27),
(1,31);
ALTER TABLE catalog_roster_policies ALTER COLUMN id RESTART WITH 2;
