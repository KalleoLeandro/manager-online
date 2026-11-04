-- Deliberately sourced synthetic attribute to exercise provenance rendering.
UPDATE catalog_player_profiles SET source_mask=1,source_name='Synthetic test fixture',source_url='https://example.invalid/test' WHERE player_id=1;
