# Vercel e Supabase

O frontend está empacotado no Spring Boot, em `backend/src/main/resources/static`.
Publicar apenas arquivos da raiz do repositório não inicia a aplicação Java.

## Configuração de publicação

O `vercel.json` da raiz declara um único serviço Container, construído com
`backend/Dockerfile`. Todas as rotas, incluindo `/`, `/api/*` e `/actuator/health`,
são encaminhadas a esse serviço sem separar o frontend do backend.

Na Vercel:

1. Selecione a raiz do repositório como **Root Directory** (`.` ou campo vazio),
   pois é nela que está o `vercel.json`. Esta configuração substitui a orientação
   anterior de selecionar `backend` como raiz do projeto Vercel.
2. Remova overrides manuais de Install Command, Build Command e Output Directory.
   O serviço é construído pelo Dockerfile, sem `npm run build` ou pasta `public`.
3. Configure as variáveis no ambiente Production: `DB_URL`, `DB_USER`,
   `DB_PASSWORD`, `APP_ENCRYPTION_KEY`, `COOKIE_SECURE=true` e `PORT=8080`.
   A URL é JDBC PostgreSQL com SSL. Não use o banco de produção em previews.
4. Publique o commit que contém esta configuração. Um redeploy de um commit
   antigo não inclui arquivos locais ainda não enviados ao GitHub.
5. No Build Log deve haver a construção do Dockerfile e `mvn -B package -DskipTests`.
   Nos Runtime Logs, uma requisição deve iniciar o Spring Boot e validar/aplicar
   as migrações Flyway. Não é um deployment estático.
6. Abra `/actuator/health`: espera-se `{"status":"UP"}`. Em seguida abra `/`
   para acessar o login. Verifique o domínio associado ao deployment Production.

Container Runtime e Services estão em beta na documentação consultada em
28/10/2026; é necessário que a conta/projeto aceite esses recursos. Caso o build
rejeite `services` ou `runtime: container`, registre o erro e confirme a
disponibilidade antes de trocar a aplicação por uma publicação estática.

## Diagnóstico

- `404 NOT_FOUND` gerado pela Vercel: conferir deployment/domínio, raiz,
  configuração de serviço e roteamento. O status Ready isolado não comprova que
  o processo Java iniciou.
- Falha ao iniciar o contêiner: consultar Runtime Logs. Conferir variáveis,
  porta 8080, conexão PostgreSQL e migrações. Não trocar senha do banco por causa
  de um 404 sem evidência de falha de autenticação PostgreSQL.
- Preview funciona, domínio principal não: conferir alias e promoção para
  Production. Não testar operações reais por um preview ligado à produção.

## Limites desta configuração

Este arquivo configura execução e roteamento; não substitui a preparação de
produção. As sessões ainda estão em memória e precisam ser persistidas antes
de uso operacional em instâncias efêmeras/múltiplas. Também faltam validar as
migrações no Supabase, os privilégios de banco e o bloqueio da exposição das
tabelas internas pela Data API. Não considerar o sistema pronto para dados
reais só porque a tela de login abriu.

Referências:
- https://vercel.com/docs/services
- https://vercel.com/docs/functions/runtimes/container
