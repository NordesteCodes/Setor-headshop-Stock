# Arquitetura e contratos

## Contextos e dependências

Catálogo (Product), estoque (StockPosition/Movement), clientes (Customer/Address), vendas (Sale/Line), financeiro (CashEntry) e autenticação são capacidades separadas em um monólito. Domínio usa apenas Java; aplicação orquestra portas. Adaptadores implementam PostgreSQL/JDBC, HTTP e transações. Wiring Spring fica fora do domínio. SQL explícito foi escolhido para tornar locks, idempotência e restrições verificáveis.

Cada comando mutável exige Idempotency-Key UUID. O adaptador transacional serializa a mesma chave com advisory lock PostgreSQL; registra escopo, fingerprint, resultado e ator na mesma transação dos efeitos. Payload diferente produz 409. Falhas fazem rollback integral e permitem retry. Locks de produtos são obtidos em ordem de UUID em entregas para evitar deadlocks. Saldo físico = disponível: não existem reservas.

## Mapeamento Stitch

Dashboard → GET /api/dashboard. Produtos/cadastro → POST /api/products cria o produto e registra opcionalmente o saldo inicial/custo como movimento na mesma transação; edição usa PUT /api/products/{id}; categorias são criadas/editadas em Produtos; exclusão só é permitida sem saldo e sem histórico. Entradas/saídas → /api/stock-movements e POST /api/stock-commands. Clientes → /api/customers e detalhe. Vendas → /api/sales, /{id}/payments, /{id}/delivery. Caixa → /api/cash e /api/cash/{id}/reverse. O dashboard e Caixa não exibem metas, projeções ou margens. A interface segue o modelo Stitch de novoModelo.zip, sem seletor de unidade fixa ou configurações sem uso. HTMLs originais preservados como referência.

Contratos detalhados disponíveis em /v3/api-docs e /swagger-ui/index.html após login. Listas retornam items, page, size, total; erro possui code, message, correlationId. HTTP 400 validação, 401 sessão, 403 CSRF, 404 recurso, 409 conflito. Frontend retém a chave de um comando sem resposta conclusiva para permitir repetição segura.

## Segurança

Senha BCrypt, TOTP RFC 6238 com segredo cifrado AES-GCM, sessão HttpOnly/SameSite e proteção CSRF. Reutilização do mesmo passo TOTP é rejeitada. Limite persistido por conta e endereço de conexão. Nenhuma API pública de cadastro. Contas provisionadas pela função de banco `provision_internal_user` começam sem segredo TOTP. Após validar senha, um desafio de cinco minutos é armazenado na sessão do servidor, sem autoridade operacional. QR gerado localmente com ZXing; confirmação válida persiste segredo cifrado e libera sessão completa. Expiração, alteração de senha, desativação e configuração concorrente invalidam o desafio. Bootstrap preservado para compatibilidade, sem alterar contas existentes. HTTPS obrigatório fora de localhost. Contas possuem permissões equivalentes conforme decisão do usuário.

## Dinheiro e datas

Dinheiro em BigDecimal/NUMERIC, arredondamento HALF_UP em centavos; custo médio em seis casas, custo da saída preservado em seis casas. Entrega com custo unitário e recebimentos imutáveis. Caixa arquivado é filtro por data, não outra tabela nem perda de histórico.

## Migrações e operação

Flyway versionado; sem ddl-auto. Migração inicial cria restrições, chaves, índices e triggers imutáveis. Antes de mudanças futuras, backup e ensaio de restauração em banco separado. Reverter aplicação somente se compatível; preferir migração corretiva, nunca editar migração já aplicada. Volumes Docker não substituem backup.
