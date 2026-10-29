# REQUIREMENTS.md — Inventory System

## Decisões aprovadas em 18 e 19/10/2026 — precedência sobre o texto original abaixo

Esta seção substitui os pontos conflitantes do escopo original. A V1 foi ampliada expressamente pelo usuário.

- Backend Java 21/Spring Boot 3, arquitetura hexagonal com DDD, monólito modular. Domínio sem dependências de framework, portas de entrada/saída e adaptadores de HTTP, segurança e PostgreSQL. Docker Compose para execução local.
- Um estoque, quantidades inteiras em unidades; a unidade é fixa e fica implícita na interface. Categorias configuráveis. O cadastro não pede descrição. Estoque seguro pode ficar sem valor; zero significa não definido. O produto registra separadamente preço de venda unitário e preço de compra unitário. A quantidade inicial pode ser zero; saldo inicial maior que zero gera um movimento de entrada e inicializa o custo médio ponderado. Preço de compra informado com saldo zero fica guardado no cadastro, sem criar custo de estoque. Sem fornecedor, código de barras ou fotos na V1.
- Custo médio ponderado: entradas recalculam custo médio; entregas/avarias preservam custo aplicado. Valores monetários BRL com centavos; custo médio com seis casas decimais.
- Formulários: novo produto inicia a categoria em “Selecione”; novo movimento inicia o produto em “Selecionar”, exigindo escolha explícita. Ao escolher um produto para entrada, preencher o preço de compra cadastrado, permitindo edição para o novo lote. Avarias mostram o custo médio aplicado, sem edição. Edição do produto preserva sua categoria.
- Compra do cliente: em todos os pontos de abertura do formulário compartilhado, “Adicionar produto” escolhe o primeiro produto ainda ausente do pedido e preenche seu preço de venda. Produtos de outras linhas ficam indisponíveis no seletor; remover/trocar libera a opção novamente. Sem produtos restantes, desabilitar a adição. Preservar validação de duplicatas no envio.
- Continuação da correção de dados de 19/10: tesoura dobrável teve o lote inicial corrigido de 4,50 para 2,50 por unidade, conforme preço de compra atualizado e solicitação do responsável. Tubetes já possuíam custo histórico de 1,00, igual ao cadastro, sem necessidade de correção. Mesma trilha auditável da correção das cuias; não generalizar alterações retroativas a qualquer edição futura de preço.
- Correção de dados aprovada em 19/10: todo o lote inicial de cuias custou 1,90 por unidade. Correção operacional explícita de custo deve preservar movimentos e custos originais, registrar motivo, operador, papel do banco e data, aplicar a revisão nas consultas de lucro e atualizar a avaliação do saldo sem alterar quantidades ou caixa. Não recalcular histórico automaticamente ao editar produto. A manutenção de lote único rejeita reposições/custos mistos e é idempotente por chave persistida.
- Contas internas individuais com senha + TOTP obrigatório. Sem distinção funcionário/administrador na V1. Toda alteração possui ator autenticado; nenhuma identidade vinda do corpo da requisição é confiável.
- Acesso aprovado posteriormente: duas contas individuais provisionadas no banco por email/senha escolhidos. Primeiro login valida senha e apresenta QR Code local; acesso operacional somente após confirmar TOTP. Logins seguintes exigem senha e código. Sem cadastro público, SMTP ou envio de QR por email; contas já configuradas preservadas.
- Clientes: nome e endereço de entrega obrigatórios (rua com número no mesmo campo, bairro e cidade-UF). CEP, CPF/CNPJ, telefone, email, complemento e referência opcionais. UF e número não são campos separados na interface. Pedido preserva cópia do endereço.
- Venda interna com múltiplos itens e preços efetivos. Carrinho não reserva/baixa estoque. Confirmação cria pedido a receber; entrega integral baixa estoque atomicamente. Sem entregas parciais inicialmente. Não permitir entrega repetida ou estoque insuficiente.
- Pagamento independente da entrega: pendente/parcial/pago calculado pelos recebimentos. Recebimento aumenta caixa e reduz dívida atomicamente, sem nova baixa física. Pagamento na criação é opcional. Fiado permitido; dívida não é automaticamente inadimplência. Sem política de devolução de cliente.
- Interface de pedidos: título “Montar compra do cliente”. Valores monetários aceitam o formato brasileiro com milhares e vírgula decimal (ex.: `2.150,99`) e decimal com ponto (`2150.99`). A interface normaliza para decimal com duas casas antes do envio; totais do carrinho são calculados em centavos inteiros. Validar campos, produtos repetidos, total positivo e recebimento acima do total com mensagem visível, preservando o formulário em caso de erro.
- Caixa: saldo inicial, aportes, retiradas, despesas e recebimentos; despesas manuais como frete. Venda não recebida não aumenta caixa. Compra paga é saída separada; custo da mercadoria vendida não gera saída de caixa na entrega.
- Avaria baixa estoque com custo e motivo. Ressarcimento de fornecedor fica fora da interface V1; registros históricos, se existirem, permanecem preservados. Sem módulo de fornecedores.
- Movimentos de caixa e estoque imutáveis; correções financeiras por estorno com referência e usuário. Recebimento estornado reabre o saldo devedor correspondente. Não oferecer estorno isolado de baixa física de venda sem política de devolução aprovada.
- Histórico financeiro superior a dois anos arquivado logicamente, consultável, incluído no saldo e nunca apagado automaticamente.
- Clientes: grid, detalhe, dívida, pedidos, pagamentos e entregas pendentes. O dashboard segue o frame Figma `CKd4shypr1Yr2ffMlbtbmF / 1:621`: cards compactos, curva de faturamento e lucro bruto, composição de valores, giro físico e tabela exclusiva de pedidos criados hoje. Gráficos permitem hoje, últimos sete dias e mês vigente; o card de lucro é sempre do mês vigente. Lucro bruto e faturamento realizado são reconhecidos quando a venda está entregue e integralmente quitada, na data mais recente entre entrega e quitação. O lucro deduz o custo médio registrado na entrega, sem deduzir despesas operacionais. A composição representa estoque ao custo, caixa positivo e contas a receber; caixa negativo é informado separadamente e não gera fatia negativa. Não há card de caixa no dashboard. A tabela de hoje possui filtros de pagamento/entrega, resumo dos itens e paginação. A área Caixa mostra saldo e histórico, com referência ao pedido para consultar seus itens. Custo médio e valor agregado aparecem nos detalhes do produto; não há margem potencial, projeção mensal ou metas na interface V1.
- As demais páginas seguem os frames do arquivo Figma compartilhado em `1:620`, com filtros em faixa própria, indicadores com ícones, tabelas e cores de pagamento/movimentação consistentes. Clientes têm painel de consulta com endereço, dívida e pedidos recentes. Não há exportação de página nem botão de montar compra no painel rápido de clientes. Os detalhes do pedido permitem exportar `.xlsx` com resumo, itens e recebimentos, usando dados autenticados e o endereço preservado no pedido. Elementos de demonstração do design não autorizam metas, ressarcimentos, rastreio de motoboy, emissão fiscal ou integrações retiradas do escopo.
- Ajustes visuais aprovados: dashboard mostra total físico de produtos (soma das quantidades) em azul, “Produtos vendidos no mês” em verde e “Avarias encontradas” em vermelho; sem registrar entrada no dashboard. Gráficos têm dicas ao passar o mouse, tocar ou focar pelo teclado. Cards de clientes são compactos; iniciais e nome selecionam o cliente. Dívida positiva amarela e zero verde; entregas pendentes positivas laranja com pulso leve (desativado com redução de movimento), zero verde. Nos pedidos, total neutro, recebido integral verde, saldo a receber positivo laranja e zero verde. Em Vendas & Entregas, aguardando entrega e pagamento pendente ficam verdes quando zerados, amarelos quando positivos; entregues e quitadas no mês ficam azuis. A ação de estoque chama-se “Registrar movimento”. Todas as fontes foram aumentadas em 2 px, incluindo tamanhos responsivos. O percentual de lucro no dashboard é sobre o custo dos produtos vendidos (lucro bruto ÷ custo × 100), sem percentual quando o custo é zero. Custos históricos na entrega não são recalculados por edição de preço no cadastro.
- O usuário pode excluir um produto do catálogo mesmo com saldo ou histórico. A exclusão é lógica: desativa e oculta o produto das consultas operacionais, preservando o saldo, os pedidos e as movimentações já registrados. A interface exige confirmação digitando o nome completo.
- WhatsApp, pagamento via provedor, RFID, multi-tenancy, SaaS e fiscal permanecem futuros. WhatsApp exigirá pagamento integral verificado antes da entrega.
- Assunções operacionais deste incremento: entrega integral, moeda BRL, calendário America/Sao_Paulo. Alterar preço de compra/venda não recalcula o custo ou os valores históricos já registrados. A compra unitária informada no cadastro é o custo da entrada inicial; novas entradas seguem o custo digitado na movimentação e atualizam o custo médio. Sem reservas, estoque pode acabar entre pedido e entrega.

As decisões acima autorizam clientes, vendas internas, recebimentos e financeiro apesar das exclusões originais. O AGENTS.md continua aplicável, exceto sua matriz de perfis (substituída por contas individuais com acesso equivalente).

## 1. Purpose
An internal inventory management system for one company. It must be industry-agnostic: the same product/catalog/inventory concepts should work for a headshop, grocery store, convenience store, or distributor. The company may later commercialize the interface/product, but this is NOT a V1 SaaS requirement.

## 2. Current scope (V1 — build now)
### 2.1 Product catalog
- Create, view, edit, search, filter, and deactivate products without losing transaction history.
- Product fields in V1: internal ID, SKU (unique when supplied), name, category, sale price, purchase price, optional safe-stock threshold, active status, and timestamps. Do not expose description or a unit selector in the interface.
- Categories are configurable and industry-agnostic. Quantities use whole units in V1; keep the fixed unit implicit in the UI. Initial quantity and its purchase unit price may be registered atomically while creating a product. Product stock may be zero.
- Define whether barcode, variations, suppliers, cost, and price are needed by inspecting the Stitch UI and confirming ambiguities; do not invent mandatory business fields.

### 2.2 Stock management
- Show current quantity by product and distinguish stock balance from movement history.
- Register inbound stock, outbound stock, and manual corrections with reason and actor.
- Prevent negative stock by default. Validate availability server-side and atomically under concurrency.
- Maintain immutable movement records and explain how each current balance was derived.
- Make retriable stock-changing commands idempotent. Reject conflicting payloads under the same key.
- Provide a basic physical stock count and reconciliation workflow if represented in the approved Stitch frontend; otherwise treat as a proposed next increment, not an assumed implemented feature.

### 2.3 Internal users and access
- Authenticated internal access. The two V1 accounts have equivalent operational permissions; record the authenticated actor for stock and financial changes.
- Record which authenticated user performed stock-changing actions.
- No public customer accounts, storefront, shopping carts, payment gateway, or customer messaging in V1.

### 2.4 Dashboard and UI
- The existing Stitch-defined frontend is the visual/interaction reference. Reuse its established layouts, colors, dark mode, responsiveness, and navigation where applicable.
- Replace placeholders and mock data with real API calls; provide loading, empty, error, and validation states.
- Provide at least a product listing, product form, stock balances, movement history, and inbound/outbound actions; map these to the actual supplied Stitch screens before building new ones.
- Summary metrics must derive from real data. Never present simulated metrics as live figures.

### 2.5 Backend and database
- Java 21 + Spring Boot, PostgreSQL, versioned Flyway migrations.
- REST API with OpenAPI documentation, validation, pagination, and consistent errors.
- Modular monolith, suitable for deployment as one backend service and one database.
- SQLite is not the production source of truth; PostgreSQL is authoritative.
- RabbitMQ is optional and excluded from initial deployment unless a concrete V1 asynchronous requirement is approved.

## 3. Required business rules
1. Every committed stock change produces a movement record with product, quantity, type, reason/reference, actor, and timestamp.
2. A repeated identical request with the same idempotency key does not repeat a movement; a changed request with the same key fails.
3. Concurrent stock deductions cannot oversell available quantity.
4. Failed transactions leave no partial stock change.
5. Deactivating or deleting a product never deletes its historical movements. Catalog deletion is logical and may proceed regardless of current balance or history.
6. Categories are configurable and not specific to a shop segment. The V1 product form does not ask for a unit because it currently supports only individual units.
7. Physical inventory observations and RFID reads (future) are not automatic confirmed stock adjustments.
8. Stock data is owned by the backend/database, never by frontend local state.

## 4. Out of scope for V1
- WhatsApp integration, customer-facing catalog, carts, checkout, payments, automated customer messages.
- RFID hardware, tag enrollment, scan sessions, and reader synchronization.
- RabbitMQ deployment, event-driven microservices, multi-tenancy, SaaS subscriptions, licensing, marketplaces.
- Complex purchasing/accounting/tax/fiscal modules unless later specified.

## 5. Future requirements (planning only — do not implement now)
### Phase 2 — WhatsApp and commercial workflows
- Customer catalog based on actual inventory; customer carts and order workflow.
- Cart does NOT reserve stock by default. Reservations, if introduced, must have explicit expiration and availability rules.
- If Client A has 500 units in cart and Client B's confirmed purchase reduces available stock to 384, detect the affected cart and offer Client A to adjust to 384 or keep it pending for replenishment. Never imply 500 are still available.
- Customer alerts require consent and applicable WhatsApp Business message/template rules. Deduplicate notifications and support retries.
- Payments must be verified with the provider, never from a screenshot alone. Duplicate provider events must not duplicate stock effects.
- Introduce durable transactional outbox and evaluate RabbitMQ when asynchronous notifications or integrations justify operational complexity.

### Phase 3 — RFID inventory
- Support UHF EPC Gen2 reader integration via a separate adapter/service; keep core inventory independent of hardware.
- Register tag EPCs and associate tags with product units or packaging according to an explicit business model.
- Read many tags simultaneously, deduplicate observations per scan session, reconcile physical findings with expected stock, and require confirmed adjustment before modifying stock.
- Handle intermittent connectivity and retried batches idempotently.

## 6. Acceptance criteria for first usable release
- An authorized user can register a product, receive stock, issue stock, inspect current balance, and review movement history.
- A duplicate inbound/outbound command has only one effect.
- Two concurrent outbound requests cannot result in negative stock.
- Data persists after service restart and schema is created through repeatable deployment of versioned migrations.
- Existing Stitch frontend screens display real backend data and handle empty/loading/error states.
- Application starts from documented instructions with example environment variables and passing relevant automated tests.

## 7. Open decisions before implementation
- Confirm the actual Stitch source files, framework, routes, screens, and component inventory.
- Confirm whether later versions need multiple physical stock locations, fractional units, product variations, suppliers, barcode support, and case-to-unit conversions.
- Confirm user roles/permissions, deployment target, backup policy, and whether V1 needs product costing.
- Clarify the business meaning of stock issuance (sale, loss, transfer, consumption) and whether any workflow requires approval.

Any undecided feature must be surfaced for decision, not silently hardcoded.
