# Estoque Interno

Sistema interno agnóstico ao ramo, Java 21/Spring Boot 3.5, PostgreSQL 17 e frontend HTML/CSS/JavaScript servido pelo backend. Arquitetura hexagonal com domínio puro, portas e adaptadores; teste arquitetural impede dependências de framework no domínio/aplicação. Referência visual: Stitch original preservado em `stitch_headshop_inventory_system/`; dashboard atualizado conforme o [frame aprovado no Figma](https://www.figma.com/design/CKd4shypr1Yr2ffMlbtbmF/Sem-t%C3%ADtulo?node-id=1-621).

O dashboard apresenta indicadores de estoque, contas a receber, lucro bruto mensal, balanço físico e pedidos de hoje. Os gráficos alternam entre hoje, sete dias e mês vigente; faturamento realizado e lucro bruto consideram vendas entregues e totalmente quitadas. A tabela mostra somente pedidos criados hoje, com filtros de pagamento/entrega, itens e acesso ao pedido. A composição de valores inclui estoque ao custo, caixa positivo e recebíveis; o saldo completo do caixa continua na página Caixa.

As páginas de catálogo, clientes, movimentações, vendas e caixa compartilham os componentes dos frames importados no Figma. Clientes contam com painel de consulta rápida, indicadores compactos e pedidos recentes. Campos monetários aceitam `2.150,99`, `2150,99` ou `2150.99`; o formulário “Montar compra do cliente” apresenta total e saldo a receber antes da confirmação. Nos detalhes do pedido, “Exportar Excel” baixa um `.xlsx` com as abas Pedido, Itens e Recebimentos. As páginas não têm exportação genérica. Os gráficos do dashboard mostram valores ao passar o mouse ou usar o foco do teclado.

## Executar no Windows com Docker Desktop (Linux containers)

Para publicação em Vercel com PostgreSQL Supabase, veja [configuração de deploy](docs/DEPLOY_VERCEL.md).

```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-local.ps1 -Username seu.usuario
docker compose up --build -d
docker compose ps
```

Abra http://localhost:8080. Para criar as contas de vocês com email/senha escolhidos, no Ubuntu/WSL execute `bash scripts/create-user.sh`; no Windows PowerShell, execute `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/create-user.ps1`. O comando cadastra no banco; cada pessoa configura seu autenticador por QR Code no primeiro acesso. Veja [cadastro de usuários](docs/USERS.md). Não há cadastro público nem senha padrão.

O bootstrap continua disponível para instalação e compatibilidade: `.local/acesso-local.txt` contém o acesso inicial gerado. Se `BOOTSTRAP_TOTP_SECRET` estiver vazio, a conta nova configura 2FA na tela; se preenchido, mantém o comportamento anterior. Uma conta já existente nunca é alterada pelo bootstrap. Depois do primeiro início remova `BOOTSTRAP_PASSWORD` e `BOOTSTRAP_TOTP_SECRET` do `.env`. Preserve `APP_ENCRYPTION_KEY` com segurança e no processo de backup. Para novas contas use o cadastro pelo banco, sem mudar a configuração do serviço.

Não existe recuperação automática de segundo fator nesta entrega. A recuperação operacional exige acesso ao banco e rotação controlada das credenciais; não publique o sistema antes de definir esse procedimento e o responsável. Fora de localhost, use proxy HTTPS, `COOKIE_SECURE=true`, controle de rede e segredo gerenciado. Compose publica somente em 127.0.0.1.

## Primeiro fluxo

1. Produtos → cadastrar categoria e produto, informando preço de venda, preço de compra, estoque seguro e saldo inicial quando houver. O custo médio inicial usa o preço de compra.
2. Entradas & Saídas → registrar entradas posteriores com quantidade/custo. Isso recalcula o custo médio e não paga a compra automaticamente.
4. Clientes → cadastrar nome/endereço obrigatórios.
5. Vendas → montar itens e informar recebido agora (zero para fiado).
6. Pedido → confirmar saída somente quando todos os produtos saírem do almoxarifado.
7. Registrar recebimentos posteriores dentro do pedido; consultar dívida no cliente.
8. Caixa → despesas, compra paga, aporte, retirada ou saldo inicial. Estorno mantém auditoria.

Pedidos não reservam estoque. Não há entrega parcial. Pagamento e entrega são independentes. Produtos excluídos saem do catálogo e mantêm os movimentos e pedidos anteriores. Sem contabilidade fiscal, projeções ou integração bancária. Gastos manuais não alteram o custo médio dos produtos.

### Correção operacional de custo inicial

Editar o preço de compra não altera custos históricos. Para corrigir um erro confirmado em um único lote inicial, a manutenção local `OpeningCostRepairCommand` passa pelo serviço de estoque, em transação e com bloqueio do saldo. Exige motivo, operador identificável e UUID estável da operação. Aceita apenas uma entrada seguida de entregas/avarias com custo uniforme e saldo conciliado; rejeita reposições, custos mistos e segunda correção do mesmo produto. Não altera caixa nem quantidades.

Após a migração V5, execute no terminal PowerShell ou Bash, substituindo os argumentos (o custo usa ponto decimal):

```sh
docker compose exec -T app java "-Dloader.main=br.com.inventory.inventory.adapter.maintenance.OpeningCostRepairCommand" -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher UUID-DA-OPERACAO UUID-DO-PRODUTO 1.90 operador-identificavel "Motivo confirmado da correcao"
```

A identidade operacional e o papel do banco são gravados em `opening_cost_repair`; as referências e os custos anteriores ficam em `opening_cost_repair_movement`. Ambos são imutáveis. As consultas usam `effective_stock_movement` e `effective_sale_line`; os lançamentos originais permanecem nas tabelas base. Os detalhes do movimento apresentam custo original e referência da correção. Repetir os mesmos argumentos devolve a operação original; reutilizar o UUID com outros dados falha.

A V5 é aditiva e não corrige produtos automaticamente. Faça backup antes de manutenção. Rollback do aplicativo após uma correção exige restaurar um backup consistente ou implementar uma compensação revisada; não apague a auditoria nem remova apenas as views, pois o saldo e os resultados precisam continuar coerentes. A ferramenta não substitui reconciliação de múltiplos lotes.

## Testes

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test.ps1
node --check backend/src/main/resources/static/app.js
```

O script usa Maven/Java 21 dentro do Docker e PostgreSQL descartável Testcontainers. Requer acesso ao socket Docker; não usa o banco da aplicação. Relatórios em `backend/target/surefire-reports/`. A imagem de execução roda como usuário sem privilégios.

Teste de navegador em banco isolado (dados sintéticos identificados como QA):

```powershell
npm ci
npx playwright install chromium
docker compose -p inventory-e2e -f compose.yaml -f compose.e2e.yaml up -d --no-build
$env:E2E_BASE_URL='http://localhost:8081'
npm run test:e2e
docker compose -p inventory-e2e -f compose.yaml -f compose.e2e.yaml down
```

A imagem local deve ter sido construída antes. Os testes criam contas sintéticas pelo banco isolado e configuram seus autenticadores, sem ler senhas pessoais. Não execute o teste no banco operacional. Capturas da interface em `.local/screenshots/`. O teste pula explicitamente URLs fora da porta isolada 8081.

## API e operação

- Contrato OpenAPI: `/v3/api-docs` (autenticado). Interface Swagger: `/swagger-ui/index.html`.
- Health: `/actuator/health` (sem detalhes públicos).
- API usa sessão persistida no PostgreSQL (migração V6), com 30 minutos de inatividade, e CSRF. `/api/auth/session` retorna identidade e token em uma requisição; `/api/auth/csrf` continua disponível. Comandos exigem `Idempotency-Key` UUID, inclusive recebimentos e estornos.
- Flyway aplica migrações no início. Banco persiste no volume `inventory-manager_postgres-data`.
- `docker compose stop` para parar; `docker compose start` para retornar. Não use `down -v` se quiser preservar dados.
- Histórico financeiro acima de dois anos é arquivado logicamente por data, permanece consultável e compõe o saldo.

Leia [arquitetura](docs/ARCHITECTURE.md), [operação e backups](docs/OPERATIONS.md) e [requisitos atualizados](REQUIREMENTS.md).
