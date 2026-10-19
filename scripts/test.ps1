$ErrorActionPreference = 'Stop'
$backendPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'backend'
docker run --rm --mount "type=bind,source=$backendPath,target=/workspace" --mount 'type=volume,source=inventory-maven-cache,target=/root/.m2' --mount 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock' -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /workspace maven:3.9.11-eclipse-temurin-21 mvn -B -ntp verify
if ($LASTEXITCODE -ne 0) { throw 'Testes falharam.' }
