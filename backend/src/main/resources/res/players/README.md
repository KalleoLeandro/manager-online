# Retratos dos jogadores

Coloque aqui um PNG chamado **`<id-do-jogador>.png`**. Exemplo: `2960.png` é o retrato de Giorgian de Arrascaeta. O ID aparece no campo `id` de `GET /api/catalog/teams/{id-do-time}/players` e na tabela `catalog_players`.

A imagem fica disponível em `/api/players/2960/portrait`. Basta salvar ou substituir o arquivo e recarregar a página; não é necessário editar o JSON, atualizar manifesto ou reiniciar o backend. Se faltar o arquivo ou ele não for PNG, a API responde com a imagem genérica.

O componente Angular enquadra automaticamente a cabeça e os ombros nas fotos que mostram muito do corpo, usando a transparência ou um fundo sólido uniforme. O ajuste é visual, calculado a partir da imagem já carregada, sem consultas adicionais ou alterações no arquivo original. Fotos que já destacam o rosto e fotografias sem silhueta identificável mantêm o enquadramento original.


Os PNGs e os relatórios de download desta pasta são ignorados pelo Git. Só este README é versionado. O downloader preserva arquivos manuais. No Docker esta pasta é montada em `/app/portraits`; fora do Docker, execute o backend a partir de `backend/` ou configure `PLAYER_PORTRAITS_DIR` com o caminho absoluto desta pasta.
