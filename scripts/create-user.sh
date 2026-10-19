#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

docker_cli=''
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  docker_cli='docker'
elif command -v docker.exe >/dev/null 2>&1 && docker.exe info >/dev/null 2>&1; then
  # In WSL or Git Bash, use Docker Desktop's Windows client when the Linux
  # CLI has no configured daemon socket but Windows Docker Desktop is running.
  docker_cli='docker.exe'
else
  printf '%s\n' 'Nao foi possivel conectar ao Docker a partir deste terminal.' >&2
  printf '%s\n' 'Se o Compose foi iniciado no Windows, execute no PowerShell: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/create-user.ps1' >&2
  printf '%s\n' 'Para usar Ubuntu/WSL, habilite esta distribuicao em Docker Desktop > Settings > Resources > WSL Integration e abra um novo terminal.' >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  printf '%s\n' 'Python 3 nao encontrado. Instale python3 para preparar a consulta com seguranca.' >&2
  exit 1
fi

read -r -p 'E-mail do usuario: ' email
if [[ ${#email} -gt 254 || ! $email =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
  printf '%s\n' 'E-mail invalido.' >&2
  exit 1
fi

read -r -s -p 'Senha desejada (minimo 14 caracteres): ' password
printf '\n'
trap 'unset password' EXIT

# The Python helper validates the DB's character/byte limits and escapes SQL
# literals. The secret and generated SQL travel only through stdin, never argv.
if ! printf '%s\n%s\n' "$email" "$password" \
  | python3 -c '
import sys

email = sys.stdin.readline().rstrip("\n")
password = sys.stdin.readline().rstrip("\n")
if len(password) < 14 or len(password.encode("utf-8")) > 72 or "\x00" in password:
    raise SystemExit("Senha deve ter pelo menos 14 caracteres e no maximo 72 bytes.")

def quote(value):
    return "\x27" + value.replace("\x27", "\x27\x27") + "\x27"

print("BEGIN; SET LOCAL standard_conforming_strings=on; SET LOCAL log_statement=\x27none\x27; SET LOCAL log_min_error_statement=\x27panic\x27; SELECT provision_internal_user(" + quote(email) + ", " + quote(password) + "); COMMIT;")
' | "$docker_cli" compose exec -T db psql -U inventory -d inventory -v ON_ERROR_STOP=1; then
  printf '%s\n' 'Nao foi possivel criar a conta. Confirme que o Docker Compose esta iniciado, que a migracao V2 foi aplicada e que o e-mail ainda nao existe.' >&2
  exit 1
fi

printf '%s\n' 'Conta criada. Entre com e-mail e senha para configurar o autenticador na tela.'
