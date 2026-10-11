#!/usr/bin/env bash
# 独立临时 MySQL，测试结束删除本脚本创建的容器；不访问业务库或 GitHub CI。
set -euo pipefail
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$task_root"
mkdir -p logs/P0-12
export ERP_MIGRATION_TEST_CONTAINER="erp-v2-migration-it-$(date +%s)-$$"
export ERP_LOGIN_TEST_REDIS_CONTAINER="${ERP_MIGRATION_TEST_CONTAINER}-redis"
export MYSQL_ROOT_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
export ERP_MIGRATION_TEST_PASSWORD="$MYSQL_ROOT_PASSWORD"
export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
docker_local() {
    env -u DOCKER_HOST -u DOCKER_CONTEXT -u DOCKER_TLS -u DOCKER_TLS_VERIFY -u DOCKER_CERT_PATH \
        docker --host=unix:///var/run/docker.sock "$@"
}
cleanup() {
    docker_local rm -f "$ERP_MIGRATION_TEST_CONTAINER" >/dev/null 2>&1 || true
    docker_local rm -f "$ERP_LOGIN_TEST_REDIS_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker_local info --format '{{.ServerVersion}}'
docker_local run -d --name "$ERP_MIGRATION_TEST_CONTAINER" \
    -p 127.0.0.1::3306 -e MYSQL_ROOT_PASSWORD mysql:8.4 >/dev/null
docker_local run -d --name "$ERP_LOGIN_TEST_REDIS_CONTAINER" \
    -p 127.0.0.1::6379 redis:7.4 >/dev/null
task_ready=false
for ((attempt=0; attempt<60; attempt++)); do
    if docker_local exec -e MYSQL_PWD "$ERP_MIGRATION_TEST_CONTAINER" \
            mysql -h127.0.0.1 -uroot -N -e 'SELECT 1' >/dev/null 2>&1; then
        task_ready=true
        break
    fi
    sleep 1
done
if [[ "$task_ready" != true ]]; then
    echo 'Temporary MySQL did not become ready within 60 seconds.' >&2
    exit 1
fi
task_port="$(docker_local port "$ERP_MIGRATION_TEST_CONTAINER" 3306/tcp)"
task_port="${task_port##*:}"
export ERP_MIGRATION_TEST_URL="jdbc:mysql://127.0.0.1:${task_port}/mysql?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
task_redis_port="$(docker_local port "$ERP_LOGIN_TEST_REDIS_CONTAINER" 6379/tcp)"
export ERP_LOGIN_TEST_REDIS_PORT="${task_redis_port##*:}"
# TLS 关闭仅限此临时 loopback 数据库，不用于部署配置。
task_maven_args=(-B -f erp-front/pom.xml -Pmigration-it verify)
if [[ -n "${ERP_MAVEN_SETTINGS:-}" ]]; then
    task_maven_args=(-s "$ERP_MAVEN_SETTINGS" "${task_maven_args[@]}")
fi
env -u DOCKER_HOST -u DOCKER_CONTEXT -u DOCKER_TLS -u DOCKER_TLS_VERIFY -u DOCKER_CERT_PATH \
    "${ERP_MAVEN_BIN:-mvn}" "${task_maven_args[@]}"
