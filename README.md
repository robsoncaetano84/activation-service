# activation-service

Serviço que recebe solicitações de ativação de produtos de clientes, guarda cada uma no banco e faz a ativação em um provedor externo, em segundo plano.

Java 21, Spring Boot 3.5, PostgreSQL 16, Flyway, Spring Security (Resource Server com JWT), Testcontainers, WireMock e ArchUnit.

## Sumário

- [Como executar](#como-executar)
- [API](#api)
- [Decisões técnicas](#decisões-técnicas)
- [Estratégia de testes](#estratégia-de-testes)
- [Cenário de análise](#cenário-de-análise)
- [Produção](#produção)
- [Pontos pendentes](#pontos-pendentes)
- [Melhorias futuras](#melhorias-futuras)
- [Uso de IA](#uso-de-ia)

## Como executar

Pré-requisitos: Java 21 e Docker.

### Tudo com Docker Compose

```bash
docker compose up --build
```

Sobe quatro serviços:

| Serviço | Porta | Para que serve |
| --- | --- | --- |
| `app` | 8080 | A aplicação |
| `postgres` | 5432 | Banco de dados |
| `oauth2` | 9000 | Emissor de tokens de teste ([mock-oauth2-server](https://github.com/navikt/mock-oauth2-server)) |
| `provider-mock` | 8089 | Provedor externo simulado com WireMock |

Com tudo no ar, em outro terminal:

```bash
# 1. Pede um token com permissão de escrita e leitura
TOKEN=$(curl -s -X POST localhost:9000/default/token \
  -d grant_type=client_credentials -d client_id=sistema-origem -d client_secret=qualquer \
  -d "scope=activation:write activation:read" \
  | tr -d ' \n\r' | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
echo "${TOKEN:0:20}"   # tem que começar com eyJ

# 2. Cria uma solicitação
curl -i -X POST localhost:8080/api/v1/requests \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"externalId":"EXT-100","customerId":"CUST-1","productCode":"INTERNET-500"}'

# 3. Consulta pelo id devolvido (em 2 ou 3 segundos o status vira COMPLETED)
curl -s localhost:8080/api/v1/requests/<id> -H "Authorization: Bearer $TOKEN"
```

O provedor simulado responde de acordo com o `productCode`, para dar para ver cada caminho da integração:

| `productCode` | O provedor responde | Resultado |
| --- | --- | --- |
| qualquer outro | 200 | `COMPLETED` |
| `PRODUTO-INVALIDO` | 422 | `FAILED` na hora, sem nova tentativa |
| `PRODUTO-INSTAVEL` | 503 com `Retry-After: 10` | Volta para `PENDING` e tenta de novo a cada 10 s, até virar `FAILED` na quinta tentativa |
| `PRODUTO-LENTO` | 200 depois de 8 s | Estoura o timeout de leitura (5 s) e é tratado como falha temporária |

### Testes

```bash
./mvnw verify
```

Roda os testes unitários (Surefire) e os de integração (Failsafe, classes `*IT`). Os de integração sobem um PostgreSQL de verdade com Testcontainers, então o Docker precisa estar rodando.

### Aplicação pela IDE

```bash
docker compose up -d postgres oauth2 provider-mock
./mvnw spring-boot:run
```

### Variáveis de ambiente

| Variável | Padrão | Uso |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/activation` | Banco |
| `DB_USER` / `DB_PASSWORD` | `activation` | Credenciais do banco |
| `PROVIDER_BASE_URL` | `http://localhost:8089` | Provedor externo |
| `PROVIDER_CONNECT_TIMEOUT` / `PROVIDER_READ_TIMEOUT` | `2s` / `5s` | Timeouts da chamada ao provedor |
| `JWT_ISSUER_URI` | `http://localhost:9000/default` | Emissor dos tokens |
| `JWT_AUDIENCE` | `activation-service` | Audiência que o token precisa ter |
| `ACTIVATION_WORKER_ENABLED` | `true` | Liga ou desliga o processamento em segundo plano |

Se uma configuração obrigatória faltar, a aplicação não sobe e o erro diz qual é.

## API

### `POST /api/v1/requests`

Escopo `activation:write`.

```json
{
  "externalId": "EXT-12345",
  "customerId": "CUST-10001",
  "productCode": "INTERNET-500"
}
```

Validação: os três campos são obrigatórios, com no máximo 64 caracteres, e aceitam só letras, números, `-` e `_`.

| Situação | Resposta |
| --- | --- |
| Solicitação nova | `201 Created`, com o header `Location` |
| Mesmo `externalId` e mesmo conteúdo (retry do sistema de origem) | `200 OK` com a solicitação que já existe |
| Mesmo `externalId` com cliente ou produto diferente | `409 Conflict` |
| Dados inválidos | `400 Bad Request` com a lista de campos |

### `GET /api/v1/requests/{id}`

Escopo `activation:read`. Devolve a solicitação e o status atual, ou `404` se não existir.

```json
{
  "id": "0b7f3c1e-5d2a-4c4e-9a51-2f8d7b1e6c90",
  "externalId": "EXT-12345",
  "customerId": "CUST-10001",
  "productCode": "INTERNET-500",
  "status": "PENDING",
  "createdAt": "2026-10-26T14:03:11.123456Z",
  "updatedAt": "2026-10-26T14:03:11.123456Z"
}
```

Datas em UTC, no formato ISO-8601.

### Erros

Todos os erros, inclusive 401 e 403, seguem o Problem Details (RFC 9457) e trazem o `traceId`, que é o mesmo id que aparece nos logs:

```json
{
  "type": "https://api.exemplo.com/problems/validation-error",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos não passaram na validação.",
  "instance": "/api/v1/requests",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [
    { "field": "customerId", "message": "Informe o cliente" }
  ]
}
```

Em erro inesperado, a resposta é um 500 genérico: o detalhe fica só no log, ligado ao `traceId`. Nenhum stack trace sai na resposta.

## Decisões técnicas

### Organização

O código fica em quatro pacotes:

| Pacote | O que tem | Pode depender de |
| --- | --- | --- |
| `api` | Controllers, DTOs, tratamento de erros, segurança, filtro de correlation id | `application`, `domain` |
| `application` | Casos de uso: criação, processamento, reconciliação, política de novas tentativas | `domain` |
| `domain` | Entidade, status e transições, repositório | nada |
| `infrastructure` | Cliente HTTP do provedor, agendamento, configurações | `application`, `domain` |

O `ArchitectureTest` (ArchUnit) quebra o build se alguma dependência for no sentido contrário ou se aparecer ciclo entre pacotes.

Não usei arquitetura hexagonal completa. A única abstração é a interface `ActivationProvider`, porque o provedor externo é o ponto que realmente muda (cliente HTTP real em produção, mock nos testes). Para o repositório, criar uma interface própria por cima do Spring Data seria só mais uma camada sem ganho.

### Estados e transições

```
PENDING → PROCESSING → COMPLETED
                    → FAILED
                    → PENDING (falha temporária, com nova tentativa agendada)
```

- As transições ficam na entidade (`startProcessing`, `complete`, `fail`, `scheduleRetry`), sem setters. Uma transição inválida, como `COMPLETED` voltar para `PROCESSING`, lança `InvalidStatusTransitionException`.
- Em falha temporária, a solicitação volta para `PENDING` com `attempts` somado e `next_attempt_at` no futuro. Escolhi isso em vez de deixar em `PROCESSING` com contador porque `PROCESSING` passa a significar só "alguém está chamando o provedor agora", o que deixa a reconciliação simples: o que está em `PROCESSING` há muito tempo está preso.
- `@Version` na entidade protege contra escrita concorrente. Se a reconciliação e o worker mexerem na mesma solicitação ao mesmo tempo, um deles recebe erro de lock otimista e o resultado é descartado, sem sobrescrever o outro.

### Duplicidade

Quem garante que não existem duas solicitações com o mesmo `externalId` é o banco, com `UNIQUE (external_id)`.

Só verificar se existe e depois inserir não basta. Duas requisições iguais podem fazer a busca ao mesmo tempo, as duas não encontram nada e as duas inserem. A constraint fecha essa janela: no PostgreSQL, o segundo insert espera o primeiro terminar e falha com violação de unicidade.

O fluxo do `ActivationRequestService.create`:

1. Busca pelo `externalId`. É só uma otimização, para o retry comum nem tentar o insert.
2. Se não achou, insere com `saveAndFlush`.
3. Se o insert falhar pela constraint, busca de novo a solicitação que ganhou a corrida.
4. Compara cliente e produto: se forem iguais é um retry e devolve `200`; se forem diferentes devolve `409`.

Duas requisições idênticas em paralelo: uma recebe `201` e a outra `200`, com a mesma solicitação. O `ActivationRequestConcurrencyIT` dispara 10 threads ao mesmo tempo contra o PostgreSQL real e confere que só um registro é criado.

O método `create` não tem `@Transactional` de propósito: no PostgreSQL, depois de um erro de constraint a transação fica abortada, e a busca do passo 3 precisa rodar em outra.

### Integração com o provedor

O `POST` não espera o provedor. Ele grava a solicitação como `PENDING` e responde na hora. Um worker (`@Scheduled`, a cada 2 s) faz a ativação em segundo plano. Assim, o tempo de resposta da API não depende do provedor, e um provedor lento ou fora do ar não derruba a API.

Cada ciclo do worker:

1. Transação curta: pega até 20 solicitações `PENDING` prontas com `FOR UPDATE SKIP LOCKED` e marca como `PROCESSING`.
2. Fora de qualquer transação: chama o provedor para cada uma.
3. Transação curta: grava o resultado.

A chamada HTTP nunca fica dentro de uma transação aberta. Se ficasse, cada chamada lenta seguraria uma conexão do banco, e com o provedor lento o pool acabaria.

O `SKIP LOCKED` faz cada instância pegar um lote diferente sem esperar as outras. Dá para rodar várias instâncias sem processar a mesma solicitação duas vezes, e sem precisar de fila ou lock distribuído.

| Retorno do provedor | Novo status | Nova tentativa? | Justificativa |
| --- | --- | --- | --- |
| Sucesso (2xx) | `COMPLETED` | Não | Ativado |
| HTTP 400 e outros 4xx | `FAILED` | Não | O pedido foi recusado. Tentar de novo dá a mesma resposta |
| HTTP 500 e outros 5xx | `PENDING` | Sim, com backoff | Erro do lado do provedor, pode ser passageiro |
| Timeout ou falha de conexão | `PENDING` | Sim, com backoff | Não sabemos se ativou. A chave de idempotência evita ativar duas vezes |
| HTTP 429 / 503 com `Retry-After` | `PENDING` | Sim, no tempo que o provedor pediu | O provedor disse quando voltar |

- **Timeouts:** 2 s de conexão e 5 s de leitura, configuráveis.
- **Timeout é ambíguo:** o provedor pode ter ativado e a resposta se perdeu. Toda chamada leva o header `Idempotency-Key` com o id da solicitação, que não muda entre tentativas. O provedor usa essa chave para não ativar duas vezes.
- **Backoff:** a espera dobra a cada tentativa (5 s, 10 s, 20 s, 40 s...) até 5 min, com até 20% de variação aleatória (jitter) para as solicitações que falharam juntas não tentarem de novo no mesmo segundo. Se o provedor mandar `Retry-After`, vale o tempo dele. A regra fica toda na `RetryPolicy`.
- **Limite:** depois de 5 tentativas, a solicitação vira `FAILED` com o último erro registrado, para análise ou reprocessamento manual.

**E se a aplicação cair entre o commit e a chamada?** A solicitação gravada continua `PENDING` no banco e é pega no próximo ciclo, de qualquer instância. A própria tabela funciona como a "caixa de saída" (outbox) do que precisa ser enviado.

**E se cair durante a chamada?** A solicitação fica em `PROCESSING`. A reconciliação (`ActivationReconciler`, a cada 1 min) devolve para `PENDING` o que está em `PROCESSING` há mais de 5 minutos. Reenviar é seguro por causa da chave de idempotência. Os 5 minutos precisam ser maiores que o pior caso de um lote: 20 solicitações x (2 s + 5 s) = 140 s.

**Circuit breaker:** não implementei. Com o processamento assíncrono, o backoff e o limite de tentativas, um provedor fora do ar não afeta a API e recebe cada vez menos chamadas. Está nos pontos pendentes.

### Correlation id

- A API aceita o header `X-Correlation-Id` (se for válido: até 64 letras, números ou `-`) ou gera um id novo. Ele vai para o MDC, aparece em todos os logs como `traceId` e volta no header da resposta e no corpo dos erros.
- Como o worker roda em outra thread, o id é gravado na solicitação (`trace_id`). O worker coloca o id de volta no MDC ao processar e envia ao provedor no header `X-Correlation-Id`. Dá para seguir uma solicitação do `POST` até a chamada ao provedor procurando um id só nos logs.
- Os logs não registram tokens nem credenciais.

### Segurança

A aplicação é um Resource Server OAuth2: não faz login nem guarda senha, só valida o JWT emitido pelo servidor de identidade.

- **Fluxo:** client credentials, porque quem chama a API são outros sistemas, não pessoas.
- **O que é validado no token:** assinatura (com as chaves públicas do emissor, via JWKS), emissor (`iss`), audiência (`aud` precisa ser `activation-service`) e expiração. Um token do mesmo emissor feito para outra API é recusado.
- **Autorização por escopo:** `activation:write` para criar e `activation:read` para consultar. O `/actuator/health/**` é público, para os health checks. Qualquer outra rota é negada por padrão.
- **CSRF desligado e sem sessão:** a API não usa cookie, o token vem em cada requisição.
- **Testes sem servidor de autenticação real:** o `ActivationRequestControllerTest` usa o `jwt()` do Spring Security Test para os casos de 401 e 403. O `JwtValidationIT` assina tokens de verdade, publica a chave num JWKS simulado com WireMock e confere que token de outro emissor, de outra audiência, expirado ou com outra assinatura recebe 401.
- **Credencial para chamar o provedor:** o provedor simulado não pede autenticação. Em produção, a credencial ficaria num cofre de segredos (AWS Secrets Manager, Azure Key Vault ou Vault) e chegaria à aplicação como variável de ambiente ou montada pelo orquestrador, nunca no repositório. A rotação seria feita com duas credenciais válidas ao mesmo tempo durante a troca. Se o provedor aceitar OAuth2, o ideal é o client credentials também nessa ponta, com token de vida curta e cache até perto de expirar.

### Banco de dados

- Migrations versionadas com Flyway. O Hibernate só valida o schema (`ddl-auto: validate`), nunca altera.
- Índice `(status, next_attempt_at)` para a consulta do worker.
- Datas em `TIMESTAMPTZ`. O relógio da aplicação corta em microssegundos, que é a precisão do PostgreSQL. Sem isso, o `POST` devolvia uma data com nanossegundos diferente da que o `GET` lê do banco.
- O `id` é um UUID gerado na aplicação. O status é gravado como texto, com uma `CHECK` constraint.

## Estratégia de testes

São 55 testes: 38 unitários e 17 de integração.

| Teste | Tipo | O que garante |
| --- | --- | --- |
| `ActivationRequestTest` | Unitário, sem Spring | Transições válidas e inválidas |
| `RetryPolicyTest` | Unitário | Backoff, jitter, `Retry-After` e limite de tentativas |
| `ActivationRequestServiceTest` | Unitário, com Mockito | Criação, retry, conflito e a corrida no insert |
| `ActivationRequestControllerTest` | `@WebMvcTest` | Contrato HTTP: 201, 200, 400, 404, 409, 500, 401 e 403, Problem Details, correlation id |
| `HttpActivationProviderTest` | WireMock | Sucesso, 400, 500, 503 com `Retry-After`, timeout, headers de idempotência e correlação |
| `ProviderPropertiesTest` | Contexto mínimo do Spring | A aplicação não sobe com configuração faltando |
| `ArchitectureTest` | ArchUnit | Sentido das dependências entre pacotes e ausência de ciclos |
| `ActivationRequestConcurrencyIT` | Integração, PostgreSQL real | 10 requisições simultâneas com o mesmo `externalId` geram um registro só |
| `ActivationProcessorIT` | Integração, PostgreSQL real, provedor mockado | Cada resultado do provedor leva ao status certo, limite de tentativas, correlation id até o provedor |
| `ActivationReconcilerIT` | Integração, PostgreSQL real | Solicitação presa em `PROCESSING` volta para a fila |
| `ActivationRequestServiceIT` | Integração, PostgreSQL real | A data devolvida na criação é a mesma que fica no banco |
| `JwtValidationIT` | Integração, aplicação completa | Validação real de assinatura, emissor, audiência e expiração |
| `HealthEndpointIT` | Integração, aplicação completa | Liveness e readiness públicos, outros endpoints do Actuator fechados |

A regra que segui: o que é lógica pura é testado sem Spring, e o que depende do banco (unicidade, `SKIP LOCKED`, precisão das datas) é testado contra um PostgreSQL de verdade. Banco em memória (H2) não reproduz o comportamento de lock nem a precisão do PostgreSQL. Os testes do worker chamam o processamento direto, sem esperar o agendamento, para não depender de tempo.

## Cenário de análise

> Tempo médio da API de 300 ms para 2,5 s, CPU em 45%, memória em 60%, algumas consultas de 50 ms para 1,2 s, serviço externo de 400 ms para 3 s.

**O que eu investigaria primeiro.** CPU e memória estão normais, então a aplicação não está trabalhando mais: ela está esperando. O primeiro passo é ver quais endpoints ficaram lentos (p95 e p99, não só a média) e o que mudou perto do início do problema: deploy, volume de dados, tráfego ou mudança no provedor.

**Como separar aplicação, banco e integração.**

- Tracing distribuído (OpenTelemetry) mostra, por requisição, quanto tempo ficou no banco, na chamada HTTP e no código.
- Sem tracing, comparo as métricas de cada parte no mesmo intervalo:
  - **pool de conexões (Hikari):** conexões ativas, threads esperando conexão e tempo para conseguir uma;
  - **chamada ao provedor:** latência e taxa de erro;
  - **banco:** tempo das consultas.
- Se o tempo de "esperar conexão" sobe junto com a latência do provedor, o banco não está lento: está sem conexão livre.
- No banco: `pg_stat_statements` para as consultas mais lentas, `EXPLAIN ANALYZE` nelas e `pg_stat_activity` para ver sessões `idle in transaction` ou esperando lock.

**Logs e métricas úteis.**
- Latência por endpoint (p95 e p99).
- Métricas do Hikari: `active`, `pending` e `acquire`.
- Latência e erros do cliente HTTP do provedor.
- Threads ocupadas do Tomcat e pausas de GC.
- Slow query log do PostgreSQL.
- Logs com `traceId` para seguir uma requisição lenta do começo ao fim.

**Os três sintomas podem ter uma causa só?** Minha hipótese: sim, com a chamada ao provedor feita dentro de uma transação, ou de forma síncrona segurando uma conexão do banco.
1. O provedor passa de 400 ms para 3 s.
2. Cada requisição fica com uma conexão do banco presa por 3 s.
3. O pool esgota.
4. As outras consultas esperam uma conexão livre, e é por isso que "algumas consultas" chegam a 1,2 s.
5. A API inteira fica lenta, enquanto a CPU continua tranquila, porque as threads só estão esperando.

Para confirmar: o aumento de `pending` no Hikari acompanha o aumento de latência do provedor, aparecem sessões `idle in transaction` no PostgreSQL, e o trace mostra o tempo "da consulta" sendo, na verdade, espera por conexão. Esse é exatamente o cenário que o desenho deste serviço evita: a chamada ao provedor fica fora da transação e fora da requisição HTTP.

A hipótese alternativa é a lentidão do banco ter outra causa, como uma consulta sem índice adequado depois que a tabela cresceu ou disputa de lock. Isso aparece no `EXPLAIN ANALYZE` e no `pg_stat_activity`.

**Melhorias possíveis.**
- Tirar a chamada remota da transação.
- Timeouts curtos no cliente HTTP.
- Processamento assíncrono.
- Circuit breaker e bulkhead para isolar o provedor.
- Índices certos para as consultas lentas.
- Revisar o tamanho do pool.

**Como validar que a correção funcionou.** Publicar primeiro em parte do tráfego (canary). Comparar, no mesmo painel e com o mesmo volume, o p95 e o p99 da API, as threads esperando conexão no Hikari, o tempo das consultas e a latência do provedor, antes e depois. Deixar alertas nesses indicadores para o problema não voltar sem ninguém ver. Se der, reproduzir antes em homologação com teste de carga (k6 ou Gatling) simulando o provedor lento.

## Produção

**Imagem:** o `Dockerfile` usa build em dois estágios. A imagem final tem só o JRE e o jar, roda com usuário sem privilégios, e a JVM usa até 75% da memória do contêiner (`MaxRAMPercentage`).

**Onde rodaria:** Kubernetes (ou um serviço gerenciado de contêineres, como ECS ou Azure Container Apps):

- **Réplicas:** pelo menos 2. Várias instâncias são seguras: a duplicidade é garantida pela constraint, e o `SKIP LOCKED` divide o processamento.
- **Probes:** liveness em `/actuator/health/liveness` e readiness em `/actuator/health/readiness`. A readiness inclui o banco e a liveness não: se o banco cair, a instância sai do balanceador, mas não é reiniciada em loop.
- **Desligamento gracioso:** a aplicação para de aceitar requisições e espera as que estão em andamento e o lote atual do worker. O `terminationGracePeriodSeconds` precisa ser maior que esse tempo, por exemplo 40 s, como o `stop_grace_period` do Compose.
- **Configuração e segredos:** configuração por variáveis de ambiente (ConfigMap). Senhas e credenciais no cofre de segredos do provedor de nuvem, nunca no repositório.
- **Banco:** PostgreSQL gerenciado (RDS, Azure Database), com backup e réplica. O Flyway roda na subida da aplicação, ou num job separado antes do deploy.
- **API e worker separados:** com `ACTIVATION_WORKER_ENABLED`, dá para ter instâncias só de API e instâncias só de worker, e escalar cada lado pela sua carga.

**Avisar outros microsserviços:** quando uma ativação termina, o serviço publicaria um evento (`ActivationCompleted` ou `ActivationFailed`) em um tópico (Kafka, SNS/SQS). Para não perder o evento se a aplicação cair entre gravar o status e publicar, o evento iria para uma tabela de outbox na mesma transação do status, e um processo separado faria a publicação.

**CI/CD:** o GitHub Actions já roda `./mvnw verify` (testes unitários e de integração com Testcontainers) e o build da imagem a cada push. Seguindo para produção, entrariam:
1. scan de vulnerabilidades da imagem;
2. publicação no registry com a tag do commit;
3. deploy em homologação com smoke test;
4. deploy em produção (canary ou rolling update), com rollback automático se os health checks ou as métricas piorarem.

## Pontos pendentes

O que o desafio pede como diferencial e não implementei, e como eu faria:

- **Listagem paginada `GET /api/v1/requests?status=&customerId=&page=&size=`:**
  - Índices:
    - `(customer_id, created_at DESC)` para filtrar por cliente, que deve ser a busca mais comum;
    - `(status, created_at DESC)` para filtrar por status;
    - `(customer_id, status, created_at DESC)` se os dois filtros forem usados juntos com frequência.
  - O índice atual `(status, next_attempt_at)` serve ao worker, mas não à listagem ordenada por data.
  - Para páginas muito profundas, paginação por cursor (keyset) em vez de `OFFSET`.
- **Isolamento entre consumidores (BOLA):**
  - Gravar na solicitação o sistema que a criou (o `sub` ou o `azp` do token).
  - O `GET` filtraria por esse dono e responderia `404` para solicitações de outro sistema.
  - A unicidade passaria a ser `(dono, external_id)`, para dois sistemas poderem usar o mesmo `externalId` sem conflito.
- **Limite de tamanho do corpo e de taxa de chamadas:** no API Gateway ou no ingress, por cliente. O corpo desta API é pequeno, então um limite baixo (alguns KB) resolve.
- **Circuit breaker (Resilience4j) no cliente do provedor:** com o provedor fora do ar, pararia de tentar por um tempo e economizaria tentativas do limite.
- **Métricas com Micrometer e Prometheus:**
  - solicitações por status;
  - latência e taxa de erro do provedor;
  - tamanho da fila `PENDING`;
  - quantidade de reconciliações.

  Hoje o Actuator expõe só o health.
- **Logs em JSON e tracing com OpenTelemetry.**
- **Motivo da falha no `GET`:** hoje o consumidor vê `FAILED`, mas o motivo fica só no banco e nos logs. Um campo `failureReason`, com um código e não a mensagem interna, ajudaria o sistema de origem.
- **ADRs em `/docs/adr`:** as decisões estão registradas neste README.

## Melhorias futuras

- **Migrar para o Spring Boot 4.x.** O desafio pedia a linha 3.x, e a 3.5.16 é a última versão aberta da linha 3.5.
- **Processar o lote do worker em paralelo com virtual threads.** Hoje as chamadas de um lote são feitas uma por vez.
- **Endpoint administrativo para reprocessar solicitações `FAILED`.**
- **Testes de contrato com o provedor (Pact) e testes de carga.**
- **Arquivar ou particionar solicitações antigas.** Isso mantém a tabela e os índices pequenos.

## Uso de IA

Usei o Claude (Anthropic) como apoio durante o desafio, como um par de programação:

- discutir as alternativas antes de cada decisão (duplicidade, processamento assíncrono, `SKIP LOCKED`, segurança) e entender os prós e contras de cada uma;
- escrever boa parte do código e dos testes a partir dessas decisões;
- revisar o projeto contra o enunciado, o que encontrou pontos que corrigi, como os testes de integração que não rodavam no `verify`, a audiência do JWT que não era validada e o correlation id que não chegava ao provedor;
- escrever este README.

Rodei todos os testes e o ambiente com Docker Compose na minha máquina, revisei o código e sei explicar cada decisão tomada.
