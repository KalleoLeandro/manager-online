INSERT INTO catalog_team_palettes (team_id,primary_color,secondary_color,away_verified,away_accent,home_accent,home_source,home_method)
SELECT id,'#006437','#ffffff',TRUE,'#333333','#ffffff','https://palmeiras.com.br/test-fixture','official' FROM catalog_teams;
INSERT INTO catalog_palette_colors SELECT id,0,'#006437' FROM catalog_teams;
INSERT INTO catalog_palette_colors SELECT id,1,'#ffffff' FROM catalog_teams;
