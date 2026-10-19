param([string]$Email)
$ErrorActionPreference = 'Stop'
if (-not $Email) { $Email = Read-Host 'E-mail do usuario' }
if ($Email.Length -gt 254 -or $Email -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') { throw 'E-mail invalido.' }
$securePassword = Read-Host 'Senha desejada (minimo 14 caracteres)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$previousEncoding = $OutputEncoding
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ($password.Length -lt 14 -or [Text.Encoding]::UTF8.GetByteCount($password) -gt 72 -or $password.Contains([char]0)) { throw 'Senha deve ter pelo menos 14 caracteres e no maximo 72 bytes.' }
    $safeEmail = $Email.Replace("'", "''")
    $safePassword = $password.Replace("'", "''")
    # stdin, never password in process arguments, command history or generated files.
    # Local Compose database owner can disable statement logging for this provisioning transaction.
    $sql = "BEGIN; SET LOCAL standard_conforming_strings=on; SET LOCAL log_statement='none'; SET LOCAL log_min_error_statement='panic'; SELECT provision_internal_user('$safeEmail', '$safePassword'); COMMIT;"
    $OutputEncoding = New-Object Text.UTF8Encoding $false
    Push-Location (Split-Path -Parent $PSScriptRoot)
    try {
        $sql | docker compose exec -T db psql -U inventory -d inventory -v ON_ERROR_STOP=1
        if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel criar a conta. Verifique se o e-mail ja existe e se a migracao V2 foi aplicada.' }
    } finally { Pop-Location }
    Write-Output 'Conta criada. Entre com e-mail e senha para configurar o autenticador na tela.'
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $securePassword.Dispose()
    $password = $null; $safePassword = $null; $sql = $null
    $OutputEncoding = $previousEncoding
}
