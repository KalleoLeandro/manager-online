# Backend

API Java 21 com Spring Boot, Maven e PostgreSQL. As telas ficam no projeto Angular em `../frontend/`.

## Organização

```text
src/main/java/br/com/manager/
├── ManagerApplication.java
├── controller/           # Rotas HTTP, validação de entrada e cabeçalhos de resposta
├── service/              # Regras de negócio, autorização e limites das transações
├── repository/           # SQL com parâmetros nomeados e acesso aos arquivos locais
├── models/
│   ├── requests/         # Corpos de entrada da API, com Bean Validation
│   ├── responses/        # Contratos de saída HTTP e WebSocket
│   ├── dto/              # Dados de atletas, clubes, consultas e arquivos
│   └── events/           # Eventos de liga, exclusão e alteração de equipe
├── config/               # Configuração de WebSocket, agendamento e relógio
├── websocket/            # Conexões, presença e entrega de eventos aos participantes
├── scheduler/            # Execução periódica da remoção de ligas inativas
├── exception/            # Tratamento comum de erros HTTP
└── utils/                # URLs de recursos locais e proteção de senhas
```

O fluxo principal é `controller → service → repository`. Controllers recebem requests e retornam responses; não consultam JDBC ou arquivos. Os services coordenam os repositories e preservam as transações e os bloqueios utilizados para vagas, escalações e exclusão de ligas. Cada request, response e DTO possui seu próprio arquivo.

`ManagerRepository` concentra contas e sessões; `SaveRepository`, ligas e participantes; `ManagementRepository`, escalações e dados dos times; `LeagueLifecycleRepository`, atividade e exclusão. `CatalogRepository`, `ClubPaletteRepository` e `RosterPolicyRepository` consultam o banco. `CatalogMigrationRepository` apoia a carga SQL inicial e a migração de bases antigas. `LocalImageRepository` acessa somente os PNGs.

Os services publicam eventos da aplicação. O transporte WebSocket entrega alterações de equipe e exclusões somente após o commit. Os services não dependem do handler WebSocket.

### Padrão de persistência

Os repositories de banco recebem `NamedParameterJdbcTemplate`. Os valores são vinculados com `MapSqlParameterSource`, usando nomes como `:saveId`, `:teamId` e `:managerId`. Os `RowMappers` ficam no próprio repository, como constantes privadas ou métodos privados quando dependem de dados auxiliares. Valores simples usam `SingleColumnRowMapper` do Spring.

```java
var parameters = new MapSqlParameterSource("saveId", id);
return db.query(
    "SELECT * FROM game_saves WHERE id=:saveId",
    parameters,
    LEAGUE_RECORD_ROW_MAPPER
).stream().findFirst();
```

Os RowMappers apenas convertem registros. Os repositories buscam as cores e as associações de campeonatos antes de entregar esses dados aos mapeadores, evitando consultas dentro de `mapRow`. A escalação é inserida em lote com parâmetros nomeados. Datas opcionais das políticas usam tipo SQL explícito, inclusive quando nulas.

As cargas iniciais em arquivos SQL continuam usando `ResourceDatabasePopulator` com o `DataSource` transacional; esses arquivos não recebem parâmetros de usuários. A limpeza de dependências de uma liga usa uma lista fixa de tabelas, enquanto o ID da liga permanece vinculado por parâmetro.

## Execução local

Com o PostgreSQL em execução e o `.env` da raiz configurado:

```powershell
node backend/dev.mjs
```

A API usa `http://localhost:8080`; o Angular usa `http://localhost:4200`. O backend carrega o `.env` ao executar pela raiz ou por `backend/`. As rotas e os formatos JSON da API foram preservados durante a organização MVC.

### Reinício automático

O comando `node backend/dev.mjs` usa o Node já necessário para o Angular, compila com o Maven Wrapper e inicia `spring-boot:run`. Ao alterar arquivos Java, YAML ou SQLs em `src/main`, recompila e aciona o Spring Boot DevTools somente após uma compilação bem-sucedida. O processo continua aberto; não é necessário parar o Angular ou o PostgreSQL. PNGs de jogadores e escudos não disparam compilação. Ctrl+C encerra os processos de backend iniciados por esse comando.

No VS Code, também é possível usar F5 com a configuração **Backend (DevTools)**. O workspace habilita a compilação automática Java, que exige as extensões Java do editor. Escolha uma forma de iniciar o backend para usar a porta 8080.

Para executar diretamente com Maven:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml spring-boot:run
```

Nesse caso, a compilação deve vir do editor ou de `mvnw compile`: [o DevTools observa o classpath compilado](https://docs.spring.io/spring-boot/4.0/reference/using/devtools.html#using.devtools.restart). O watcher resolve essa etapa automaticamente no terminal. Mudanças no `pom.xml` exigem reiniciar o comando para carregar novas dependências.

O perfil de teste desativa o reinício automático. O DevTools é opcional e está excluído do JAR distribuído. Para ativar a dependência em uma aplicação que já estava aberta, inicie o backend uma vez com um dos comandos acima; as próximas alterações de código serão recarregadas.

O catálogo e as paletas usam o PostgreSQL como fonte única. A aplicação inicia a estrutura de bancos vazios sem carregar jogadores ou clubes e migra bases antigas preservando suas ligas; não lê JSONs de catálogo nem refaz a carga a cada reinício. O esquema está em `../database/init.db`, compartilhado com o container PostgreSQL. O catálogo deve ser fornecido por cada instalação; nenhum elenco é distribuído. Fotos e escudos locais continuam independentes de consultas externas durante o uso.

## Validação

```powershell
.\backend\mvnw.cmd -f backend/pom.xml clean verify
```

Os testes usam H2 em memória e cobrem cadastro, autenticação, ligas solo e multiplayer, concorrência, autorização, atividade e expiração, escalações, imagens e WebSocket. `ArchitectureTests` verifica a separação das camadas. `clean` também remove classes compiladas de pacotes antigos após movimentar arquivos.

A configuração padrão fica em `src/main/resources/application.yaml`, com PostgreSQL. O perfil `test` usa `src/test/resources/application-test.yaml`, com H2 em memória no modo de compatibilidade PostgreSQL e limpeza automática de ligas desativada. Nos testes que carregam o contexto Spring, use `@ActiveProfiles("test")`; testes unitários sem contexto não precisam de banco. O driver H2 permanece restrito às dependências de teste.

A reorganização foi validada com 33 testes e geração do JAR. Não exige alteração do esquema do banco nem atualização de containers.
