# Fontes locais

Barlow Condensed (600, 700, 800) e DM Sans (400, 500, 600, 700) são carregadas do próprio frontend. Os arquivos foram obtidos pelo comando de preparação `node scripts/download-local-fonts.mjs`; a aplicação não consulta Google Fonts nem Google Fonts Static durante o uso.

As licenças SIL Open Font License 1.1 estão nos arquivos `barlowcondensed-OFL.txt` e `dmsans-OFL.txt`, também copiados para `assets/fonts/` no build. Os arquivos das fontes são emitidos pelo Angular junto dos demais recursos de estilo.
