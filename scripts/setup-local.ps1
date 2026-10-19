param([string]$Username = 'operador')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envPath) { throw '.env já existe. Nenhum segredo foi alterado.' }
if ($Username -notmatch '^[a-z0-9._@-]{3,80}$') { throw 'Usuário inválido.' }
function New-RandomBytes([int]$Count) {
    $buffer = New-Object byte[] $Count
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return ,$buffer
}
$dbPassword = [Convert]::ToBase64String((New-RandomBytes 32))
$encryptionKey = [Convert]::ToBase64String((New-RandomBytes 32))
$password = [Convert]::ToBase64String((New-RandomBytes 24))
$lines = @("DB_PASSWORD=$dbPassword", "APP_ENCRYPTION_KEY=$encryptionKey", "BOOTSTRAP_USERNAME=$Username", "BOOTSTRAP_PASSWORD=$password", 'BOOTSTRAP_TOTP_SECRET=', 'APP_PORT=8080', 'COOKIE_SECURE=false')
[IO.File]::WriteAllLines($envPath, $lines, (New-Object Text.UTF8Encoding $false))
$privateDir = Join-Path $projectRoot '.local'
New-Item -ItemType Directory -Force -Path $privateDir | Out-Null
$access = @('ACESSO LOCAL - arquivo privado, não compartilhar nem versionar', '', "URL: http://localhost:8080", "Usuário: $Username", "Senha: $password", '', 'Entre com usuário e senha. O sistema exibirá o QR Code para configurar seu autenticador no primeiro acesso.', 'Para criar suas contas pessoais com email e senha escolhidos, execute scripts/create-user.ps1.', '', 'Depois do primeiro início, remova BOOTSTRAP_PASSWORD e BOOTSTRAP_TOTP_SECRET do .env.', 'Guarde APP_ENCRYPTION_KEY com o backup: sem ela não será possível recuperar os autenticadores.')
[IO.File]::WriteAllLines((Join-Path $privateDir 'acesso-local.txt'), $access, (New-Object Text.UTF8Encoding $false))
Write-Output 'Configuração criada. Consulte .local/acesso-local.txt para configurar seu autenticador. Segredos não foram exibidos.'
