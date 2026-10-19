# Validação — 18/10/2026

## Atualização: catálogo e painel simplificados

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1` — BUILD SUCCESS; 24 testes, zero falhas/erros/skips. A suíte incluiu busca e ordenação de produtos, renomeação de categoria, exclusão segura, rejeição de ressarcimento fora do escopo e ausência de métricas de margem.
- `npm run check` e `node --check tests/e2e/workflow.spec.js` — aprovados.
- `E2E_BASE_URL=http://localhost:8081 npm run test:e2e` — 2 testes aprovados, incluindo cadastro com valor unitário único, busca por categoria, detalhes de custo agregado, ocultar/mostrar, pedido, recebimento e caixa.
- Compose isolado E2E encerrado. O Compose local foi reconstruído; `http://localhost:8080/actuator/health` respondeu `{"status":"UP"}`. O banco permaneceu no volume existente.

## Atualização: autenticador no primeiro acesso

- `mvn -B -ntp verify` em Java 21/Docker: 21 testes aprovados, zero falhas/erros/skips.
- Novos testes cobrem provisionamento PostgreSQL/BCrypt, decodificação do QR gerado, bloqueio da API antes de confirmar 2FA, CSRF, segredo persistido somente após código válido, expiração, senha alterada, configuração concorrente, replay e limite de tentativas.
- `npm run check`: aprovado. `scripts/create-user.ps1`: sintaxe validada pelo parser PowerShell; a função de provisionamento usada pelo script foi exercitada no PostgreSQL de teste.
- `E2E_BASE_URL=http://localhost:8081 npm run test:e2e`: 2 testes aprovados (fluxo completo de estoque e primeiro acesso/QR/logout/login posterior). Contas sintéticas criadas somente no banco isolado; sem ler credenciais pessoais.
- `docker compose up --build -d --wait`: migração V2 aplicada e serviços healthy, preservando autenticadores existentes. Novas contas ficam pendentes de configuração TOTP.

As evidências abaixo se referem ao primeiro incremento, anterior a essa atualização.

## Executado

- Maven Java 21 em Docker: `mvn -B -ntp verify` — BUILD SUCCESS, 18 testes, zero falhas/erros/skips.
- 1 teste ArchUnit: domínio/aplicação não dependem de Spring, Jakarta, Jackson ou adaptadores; domínio não depende da aplicação.
- 3 testes de domínio: custo médio/quantidades, endereço e TOTP (vetor RFC + proteção de replay).
- 14 testes PostgreSQL/Testcontainers/API: retiradas simultâneas; duplicatas concorrentes; payload conflitante; resposta perdida/retry; rollback de pedido multi-item; falha após mutação; pagamento/entrega/estorno; pagamentos concorrentes; endereço/custo históricos; restrições/imutabilidade; avaria/ressarcimento; arquivo com saldo e ator; versão otimista/desativação; sessão/CSRF/2FA e OpenAPI.
- `npm run check` — JavaScript válido.
- `E2E_BASE_URL=http://localhost:8081 npm run test:e2e` (variável definida pelo PowerShell) — 1 fluxo aprovado: login real com 2FA, categoria, produto, entrada, cliente/endereço, pedido fiado, entrega, recebimento parcial, quitação e frete manual.
- Capturas inspecionadas em desktop e viewport 390x844, temas escuro e claro. Corrigida navegação móvel e progresso de meta não definida. Tabelas mantêm rolagem horizontal em telas estreitas.
- `docker compose up --build -d --wait` — aplicação e PostgreSQL healthy.
- `docker compose ps` — app publicado somente em 127.0.0.1:8080, banco sem porta pública.
- Consulta do banco operacional confirmou 0 produtos, 0 clientes e 0 vendas: dados QA estão exclusivamente no projeto Compose `inventory-e2e`.

O primeiro ensaio Playwright falhou por seletor de teste exato em um select; foi corrigido para consulta por papel acessível. A repetição passou, incluindo nova rodada após ajustes visuais.

## Limites desta evidência

Não foram executados teste de carga, pentest, restauração de backup ou certificação de acessibilidade. Não há alegação de produção pronta. Entrega parcial, cancelamento/devolução, recuperação 2FA pela interface, exportações e integrações futuras estão descritos em OPERATIONS.md. Provisão de usuários é operacional, não uma tela administrativa.

O build da imagem usa `-DskipTests`; a suíte foi executada separadamente em Java 21 com PostgreSQL real. Nenhum teste usa SQLite/H2 como substituto de PostgreSQL.
