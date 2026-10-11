# Windows 本地隔离测试入口；只清理本次随机创建的容器，不连接业务库。
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskDockerHost = 'npipe:////./pipe/dockerDesktopLinuxEngine'
$taskId = [Guid]::NewGuid().ToString('N')
$taskMysql = "erp-v2-migration-it-$taskId"
$taskRedis = "$taskMysql-redis"
$taskEnvironment = @{}
$taskExitCode = 1
$taskPreviousLocation = Get-Location

function Set-TestEnvironment([string]$Name, [string]$Value) {
    $taskEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

function Invoke-TestDocker([string[]]$DockerArguments) {
    $taskOutput = & docker --host=$taskDockerHost @DockerArguments
    if ($LASTEXITCODE -ne 0) { throw "Local Docker command failed: $($DockerArguments[0])" }
    return $taskOutput
}

try {
    Set-Location -LiteralPath $taskRoot
    foreach ($taskName in @('DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH')) {
        Set-TestEnvironment $taskName $null
    }
    $taskRandom = [byte[]]::new(24)
    $taskRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskRng.GetBytes($taskRandom) } finally { $taskRng.Dispose() }
    $taskPassword = [BitConverter]::ToString($taskRandom).Replace('-', '').ToLowerInvariant()
    Set-TestEnvironment 'MYSQL_ROOT_PASSWORD' $taskPassword
    Set-TestEnvironment 'MYSQL_PWD' $taskPassword
    Set-TestEnvironment 'ERP_MIGRATION_TEST_PASSWORD' $taskPassword
    Set-TestEnvironment 'ERP_MIGRATION_TEST_CONTAINER' $taskMysql
    Set-TestEnvironment 'ERP_LOGIN_TEST_REDIS_CONTAINER' $taskRedis
    Set-TestEnvironment 'ERP_TEST_DOCKER_HOST' $taskDockerHost
    Invoke-TestDocker -DockerArguments @('info', '--format', '{{.ServerVersion}}')
    Invoke-TestDocker -DockerArguments @('run', '-d', '--name', $taskMysql, '-p', '127.0.0.1::3306',
        '-e', 'MYSQL_ROOT_PASSWORD', 'mysql:8.4') | Out-Null
    Invoke-TestDocker -DockerArguments @('run', '-d', '--name', $taskRedis, '-p', '127.0.0.1::6379', 'redis:7.4') | Out-Null
    $taskReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 180; $taskAttempt++) {
        & docker --host=$taskDockerHost exec $taskMysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -N -e "SELECT 1"' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $taskReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $taskReady) {
        Invoke-TestDocker -DockerArguments @('logs', '--tail', '30', $taskMysql)
        throw 'Temporary MySQL did not become ready within 180 attempts.'
    }
    $taskMysqlPort = (Invoke-TestDocker -DockerArguments @('port', $taskMysql, '3306/tcp')).Trim().Split(':')[-1]
    $taskRedisPort = (Invoke-TestDocker -DockerArguments @('port', $taskRedis, '6379/tcp')).Trim().Split(':')[-1]
    Set-TestEnvironment 'ERP_MIGRATION_TEST_URL' "jdbc:mysql://127.0.0.1:$taskMysqlPort/mysql?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    Set-TestEnvironment 'ERP_LOGIN_TEST_REDIS_PORT' $taskRedisPort
    $taskMaven = if ($env:ERP_MAVEN_BIN) { $env:ERP_MAVEN_BIN } else { 'mvn.cmd' }
    $taskMavenArguments = @('-B', '-f', 'erp-front/pom.xml', '-Pmigration-it', 'verify')
    if ($env:ERP_MAVEN_SETTINGS) { $taskMavenArguments = @('-s', $env:ERP_MAVEN_SETTINGS) + $taskMavenArguments }
    & $taskMaven @taskMavenArguments
    $taskExitCode = $LASTEXITCODE
} finally {
    # 名称来自本次 GUID，不遍历或删除用户已有容器。
    & docker --host=$taskDockerHost rm -f $taskMysql $taskRedis 2>$null | Out-Null
    foreach ($taskName in $taskEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($taskName, $taskEnvironment[$taskName], 'Process')
    }
    Set-Location -LiteralPath $taskPreviousLocation.Path
}
exit $taskExitCode

