# Operação, backup e migrações

## Backup local

Execute no diretório do projeto. O dump é criado dentro do container e copiado como arquivo binário, evitando corrupção por redirecionamento do PowerShell.

```powershell
New-Item -ItemType Directory -Force .local/backups
docker compose exec -T db pg_dump -U inventory -d inventory -Fc -f /tmp/inventory.dump
docker compose cp db:/tmp/inventory.dump .local/backups/inventory.dump
```

Copie o backup para armazenamento cifrado fora desta máquina, com acesso restrito. Preserve separadamente APP_ENCRYPTION_KEY e credenciais operacionais. Nunca envie arquivos com clientes, senhas ou segredos ao Git. Definir frequência/RPO/RTO com o responsável antes de produção; sugestão inicial a validar: diário, cópia externa e ensaio mensal.

## Ensaio de restauração (banco separado)

```powershell
docker compose exec -T db createdb -U inventory inventory_restore_check
docker compose cp .local/backups/inventory.dump db:/tmp/restore-check.dump
docker compose exec -T db pg_restore -U inventory -d inventory_restore_check --exit-on-error /tmp/restore-check.dump
docker compose exec -T db psql -U inventory -d inventory_restore_check -c "SELECT count(*) FROM stock_movement; SELECT coalesce(sum(amount),0) FROM cash_entry;"
```

O nome do banco de ensaio deve estar livre. Não restaure sobre o banco ativo sem janela de manutenção, backup atual e aprovação explícita. Verifique contagens, saldos, migrações e login em uma instância isolada antes de considerar a recuperação validada.

## Migrações

Versionadas por Flyway. Nunca editar V1 após aplicação em ambiente persistente; criar V2 etc. Antes de atualizar: backup, ensaio em cópia, deploy e health check. Sem migração destrutiva automática. Rollback por restauração implica perda das escritas posteriores ao backup; preferir correção para frente. Alterações incompatíveis exigem estratégia expand/contract.

## Limitações conhecidas do primeiro incremento

- Sem política de devolução/cancelamento de pedido entregue, entrega parcial ou reservas. Não altere diretamente as tabelas para contornar esses fluxos.
- Cadastro de usuários pelo banco (docs/USERS.md), com configuração TOTP pela interface no primeiro acesso. Sem gestão de contas ou recuperação 2FA pela interface; MFA obrigatório.
- Categorias podem ser criadas em Produtos; não há exclusão que quebre histórico. A quantidade é expressa em unidades inteiras, sem seletor redundante. Fotos, barcode, fornecedor cadastrado, caixas e conversões ficam futuros.
- Endereço confirmado é cópia histórica. Correção de endereço de pedido já confirmado ainda não tem fluxo específico.
- Sem importação, exportação CSV/PDF ou relatórios fiscais. Histórico é paginado; dados do Stitch nunca são usados como dados reais.
- Arquivamento é filtro lógico por data, não transferência física; nunca altera saldo.
- As compras não estão vinculadas a contas a pagar: entrada física e pagamento manual são operações separadas.

## Dependências

Java 21 compatível com Spring Boot 3.5: https://docs.spring.io/spring-boot/3.5/system-requirements.html
OpenAPI para Spring Boot 3: https://springdoc.org/v2/
