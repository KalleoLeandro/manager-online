# manager-online

Manager de futebol com backend Java/Maven, frontend Angular e PostgreSQL.

Copie `.env.example` para `.env` e configure a senha do PostgreSQL. Para iniciar apenas o banco:

```sh
docker compose up -d --build postgres
```

Para iniciar todos os serviços: `docker compose up -d --build`. Frontend: http://localhost:4200; backend: http://localhost:8080.

`database/init.db` contém somente o esquema, sem jogadores, clubes, contas ou ligas. O Dockerfile do banco executa esse SQL na primeira inicialização de um volume vazio. O backend também utiliza o mesmo arquivo para inicializar/atualizar a estrutura, inclusive quando executado localmente. Volumes existentes são preservados.

Cada instalação deve fornecer seu próprio catálogo no PostgreSQL. Sem essa carga, o catálogo começa vazio. Dados de carga, JSONs, fotos e escudos não são distribuídos pelo Git nem incluídos nas imagens da aplicação. PNGs locais usam o ID numérico: `backend/src/main/resources/res/players/{id}.png` e `backend/src/main/resources/res/crests/{id}.png`.

Os testes usam H2 e atletas fictícios exclusivos das fixtures de teste; esses dados não entram no JAR nem no banco da aplicação.
