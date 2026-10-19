# Duas contas individuais: cadastro pelo banco e autenticador no primeiro acesso

Não existe cadastro público nem envio de QR por email. O email identifica o login; não precisa de servidor SMTP.

## Criar uma conta

Com Docker Compose iniciado e migração V2 aplicada, no Ubuntu/WSL execute:

```bash
bash scripts/create-user.sh
```

O script solicita email e senha (a senha fica oculta) e envia a consulta ao PostgreSQL pelo stdin. Ele requer Docker Compose acessível no Ubuntu/WSL e Python 3. Se preferir, torne-o executável uma vez com `chmod +x scripts/create-user.sh` e use `./scripts/create-user.sh`.

Se aparecer `failed to connect to the docker API` ou `docker.sock`, o terminal não está conectado ao daemon. Com Docker Desktop no Windows, habilite a integração da distribuição em **Settings > Resources > WSL Integration** e abra um novo terminal. O helper também tenta usar `docker.exe` do Windows quando o cliente Linux não alcança o daemon. Como alternativa imediata, rode o comando PowerShell abaixo no PowerShell do Windows, na pasta do projeto.

No Windows PowerShell, use:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/create-user.ps1
```

Informe email e senha escolhidos quando solicitado. A senha é digitada de forma oculta; não é passada nos argumentos do processo nem gravada em arquivo. Ambos os scripts executam a função de cadastro no PostgreSQL local. Repita para a segunda pessoa. Não é necessário recriar o container ou editar `.env`.

Se preferir seu editor SQL conectado ao banco, use a função abaixo com seus próprios valores (não salve a consulta com senha real):

```sql
SELECT provision_internal_user('seu.email@exemplo.com', 'SUBSTITUA_PELA_SENHA_ESCOLHIDA');
```

A função valida o email, normaliza para minúsculas, exige senha com no mínimo 14 caracteres e no máximo 72 bytes e armazena BCrypt com custo 12. O email é único sem distinguir maiúsculas/minúsculas. A coluna `username` guarda o email, para preservar as referências existentes. `totp_cipher` começa NULL; não preencha esse campo manualmente e não grave a senha original em `password_hash`.

Somente o dono da função/banco tem permissão de execução por padrão. O helper é destinado ao banco local do Compose, onde consegue desativar registro de consultas nessa transação. Em outro ambiente, use conexão protegida e um mecanismo de provisionamento com permissões adequadas.

## Primeiro acesso

1. Abra http://localhost:8080 e informe email/senha.
2. A tela apresenta o QR Code, gerado localmente; no celular, adicione uma conta no aplicativo autenticador e escaneie.
3. Digite o código de seis dígitos. Se necessário, há uma chave manual em “Não consigo escanear”.
4. Somente após confirmar o código o sistema ativa o autenticador e libera o acesso.

A etapa intermediária expira em cinco minutos e não autoriza acesso a estoque, clientes ou caixa. Se fechar a página antes de concluir, faça login novamente e use o novo QR. Segredos de configurações abandonadas não são persistidos no banco.

Nos acessos seguintes a tela pede somente o código do autenticador depois da senha. O QR não volta a ser exposto. Logout, expiração, alteração de senha ou desativação impedem usar o desafio pendente. Duas sessões não podem substituir a configuração uma da outra. Tentativas são limitadas no servidor, e códigos já usados não são aceitos novamente.

## Contas anteriores

A migração preserva as contas e autenticadores existentes, inclusive a conta inicial `operador`, se criada anteriormente. Ela não é automaticamente convertida em uma conta sem 2FA. Novas contas cadastradas pela função usam o fluxo acima. Não criamos contas pessoais automaticamente porque emails e senhas são escolhidos por vocês.

Recuperação por perda de celular continua sendo uma operação administrativa separada; esta mudança não cria um botão que permita ignorar o segundo fator.

Referências técnicas: [pgcrypto/BCrypt](https://www.postgresql.org/docs/current/pgcrypto.html) e [ZXing QRCodeWriter](https://zxing.github.io/zxing/apidocs/com/google/zxing/qrcode/QRCodeWriter.html).
