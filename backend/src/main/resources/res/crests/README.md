# Escudos locais

Salve os escudos PNG como `<teamId>.png` nesta pasta. Consulte os IDs na tabela `catalog_teams` ou na API `GET /api/catalog`. A API `/api/teams/{id}/crest` usa esse arquivo e, quando ausente ou inválido, entrega o escudo genérico local. Não é feita consulta externa durante o uso do jogo.

Os PNGs são ignorados pelo Git e não entram no JAR. Você pode adicionar ou substituir um escudo manualmente e recarregar a página, sem atualizar manifesto nem reiniciar a aplicação. Configure `CLUB_CRESTS_DIR` quando o diretório de execução exigir um caminho absoluto.

Para baixar os escudos disponíveis do catálogo, execute `node scripts/download-club-crests.mjs` na raiz, após instalar as dependências de `scripts/`. Esse comando de preparação usa a internet e preserva arquivos existentes. Os clubes sem imagem disponível continuam usando o genérico; o relatório fica em `.tools/club-crests/report.json`.
