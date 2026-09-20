#!/usr/bin/env bash
set -Eeuo pipefail

readonly APP_DIR="${PLATEVIEW_APP_DIR:-/opt/plateview}"
readonly ENV_FILE="$APP_DIR/.env"
[[ -r "$ENV_FILE" ]] || { printf '失败：生产环境文件不存在：%s\n' "$ENV_FILE" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

readonly SOURCE_DIR="$APP_DIR/source"
readonly RUNTIME_DIR="$APP_DIR/runtime"
readonly BACKUP_DIR="$APP_DIR/backups"
readonly LOG_DIR="$APP_DIR/logs/deploy"
readonly WORK_ORDER_IMAGE_DIR="$APP_DIR/data/work-order-images"
readonly LOCK_FILE="$RUNTIME_DIR/deploy.lock"
readonly COMPOSE_FILE="$SOURCE_DIR/compose.production.yaml"
readonly CADDY_CONTAINER="${PLATEVIEW_CADDY_CONTAINER:-plateview-caddy-1}"
readonly POSTGRES_CONTAINER="${PLATEVIEW_POSTGRES_CONTAINER:-plateview-postgres-1}"
readonly BACKEND_NETWORK="${PLATEVIEW_BACKEND_NETWORK:-plateview_backend}"
readonly EDGE_NETWORK="${PLATEVIEW_EDGE_NETWORK:-plateview_edge}"
readonly PUBLIC_HEALTH_URL="${PLATEVIEW_PUBLIC_HEALTH_URL:-https://api.chenxiruyu.dpdns.org/health}"
readonly RETENTION_DAYS="${PLATEVIEW_BACKUP_RETENTION_DAYS:-14}"
readonly MIN_DISK_KIB="${PLATEVIEW_MIN_DISK_KIB:-2097152}"
readonly MIN_MEMORY_KIB="${PLATEVIEW_MIN_MEMORY_KIB:-665600}"
readonly MIN_SWAP_KIB="${PLATEVIEW_MIN_SWAP_KIB:-921600}"
readonly MAX_LOAD_ONE="${PLATEVIEW_MAX_LOAD_ONE:-1.2}"
readonly API_MEMORY_LIMIT="${PLATEVIEW_API_MEMORY_LIMIT:-512m}"
readonly API_MEMORY_RESERVATION="${PLATEVIEW_API_MEMORY_RESERVATION:-384m}"
readonly API_MEMORY_SWAP_LIMIT="${PLATEVIEW_API_MEMORY_SWAP_LIMIT:-640m}"
readonly API_CPU_LIMIT="${PLATEVIEW_API_CPU_LIMIT:-0.70}"
readonly API_PIDS_LIMIT="${PLATEVIEW_API_PIDS_LIMIT:-256}"
readonly API_JAVA_OPTIONS="${PLATEVIEW_API_JAVA_OPTIONS:--XX:MaxRAMPercentage=50 -XX:MaxMetaspaceSize=96m -XX:+ExitOnOutOfMemoryError}"
readonly IMAGE_REPOSITORY="${PLATEVIEW_API_IMAGE_REPOSITORY:-ghcr.io/jaydocoder/plateview-api}"

mkdir -p "$RUNTIME_DIR" "$BACKUP_DIR" "$LOG_DIR" "$WORK_ORDER_IMAGE_DIR"
readonly LOG_FILE="$LOG_DIR/deploy-$(date -u +%Y%m%dT%H%M%SZ)-${1:-manual}.log"
readonly API_CONTAINER_RETENTION_SCRIPT="$SOURCE_DIR/deploy/plateview-retain-api-containers.sh"
readonly API_IMAGE_RETENTION_SCRIPT="$SOURCE_DIR/deploy/plateview-retain-api-images.sh"
# shellcheck source=deploy/plateview-deploy-lib.sh
source "$SOURCE_DIR/deploy/plateview-deploy-lib.sh"
exec > >(tee -a "$LOG_FILE") 2>&1

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() { log "失败：$*"; exit 1; }
compose() { docker compose --project-directory "$APP_DIR" --project-name plateview --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }
git_source() { git --git-dir="$SOURCE_DIR/.git" --work-tree="$SOURCE_DIR" "$@"; }
write_caddy_upstream() { printf 'reverse_proxy %s:8080\n' "$1" > "$RUNTIME_DIR/Caddyfile"; }
restore_caddy_config() { cp "$RUNTIME_DIR/Caddyfile.previous" "$RUNTIME_DIR/Caddyfile"; }

read_runtime_value() {
    local name="$1"
    [[ -r "$RUNTIME_DIR/$name" ]] || return 0
    tr -d '[:space:]' < "$RUNTIME_DIR/$name"
}

container_ip() {
    docker inspect -f "{{with index .NetworkSettings.Networks \"$BACKEND_NETWORK\"}}{{.IPAddress}}{{end}}" "$1"
}

wait_for_container_health() {
    local container="$1"
    local attempts="${2:-45}"
    local ip
    ip=$(container_ip "$container")
    [[ -n "$ip" ]] || return 1

    for _ in $(seq 1 "$attempts"); do
        if curl --fail --silent --show-error --max-time 2 "http://$ip:8080/health" >/dev/null; then
            return 0
        fi
        [[ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null || true)" == "true" ]] || return 1
        sleep 2
    done
    return 1
}

record_resource_snapshot() {
    local stage="$1"
    log "资源快照[$stage]"
    uptime
    free -h
    df -h "$APP_DIR"
    docker stats --no-stream --format '容器={{.Name}} CPU={{.CPUPerc}} 内存={{.MemUsage}} 进程={{.PIDs}}' || true
}

preflight_checks() {
    local disk_available_kib memory_available_kib swap_total_kib load_one
    disk_available_kib=$(df -Pk "$APP_DIR" | awk 'NR == 2 { print $4 }')
    memory_available_kib=$(awk '/^MemAvailable:/ { print $2 }' /proc/meminfo)
    swap_total_kib=$(awk '/^SwapTotal:/ { print $2 }' /proc/meminfo)
    load_one=$(awk '{ print $1 }' /proc/loadavg)

    integer_is_at_least "$disk_available_kib" "$MIN_DISK_KIB" || die "根分区可用空间不足 2GiB：${disk_available_kib}KiB"
    integer_is_at_least "$memory_available_kib" "$MIN_MEMORY_KIB" || die "可用内存不足 650MiB：${memory_available_kib}KiB"
    integer_is_at_least "$swap_total_kib" "$MIN_SWAP_KIB" || die "交换空间不足 900MiB：${swap_total_kib}KiB"
    decimal_is_not_greater_than "$load_one" "$MAX_LOAD_ONE" || die "一分钟平均负载过高：$load_one，大于 $MAX_LOAD_ONE"
    docker info >/dev/null 2>&1 || die "Docker 服务不可用"

    if [[ -s "$RUNTIME_DIR/active-container" ]]; then
        local active_container
        active_container=$(<"$RUNTIME_DIR/active-container")
        [[ "$(docker inspect -f '{{.State.Running}}' "$active_container" 2>/dev/null || true)" == "true" ]] \
            || die "当前活动 API 未运行：$active_container"
        curl --fail --silent --show-error --max-time 10 "$PUBLIC_HEALTH_URL" >/dev/null \
            || die "部署前公网健康检查失败"
    fi

    log "部署前资源检查通过：磁盘 ${disk_available_kib}KiB，可用内存 ${memory_available_kib}KiB，交换空间 ${swap_total_kib}KiB，负载 $load_one"
}

verify_database_migrations() {
    local expected_version applied_version failed_migrations
    expected_version=$(find "$SOURCE_DIR/server/src/main/resources/db/migration" -maxdepth 1 -type f -name 'V*__*.sql' -printf '%f\n' \
        | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | tail -n 1)
    [[ -n "$expected_version" ]] || die "未找到 Flyway 迁移脚本"
    failed_migrations=$(docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
        psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc \
        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE;")
    [[ "$failed_migrations" == "0" ]] || die "Flyway 存在 $failed_migrations 条失败迁移记录"
    applied_version=$(docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
        psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc \
        "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1;")
    migration_version_is_at_least "$expected_version" "$applied_version" \
        || die "Flyway 迁移版本落后：源码要求至少 V$expected_version，实际 V${applied_version:-无}"
    log "Flyway 迁移校验通过：数据库 V$applied_version，源码要求至少 V$expected_version，失败记录 $failed_migrations 条"
}

exec 9>"$LOCK_FILE"
flock -n 9 || die "已有部署正在运行，拒绝并发部署"

if [[ "${1:-}" == "rollback" ]]; then
    [[ -s "$RUNTIME_DIR/previous-upstream" ]] || die "没有可用的上一版本上游"
    [[ -s "$RUNTIME_DIR/previous-container" ]] || die "没有可用的上一版本容器"
    previous_upstream=$(<"$RUNTIME_DIR/previous-upstream")
    previous_container=$(<"$RUNTIME_DIR/previous-container")
    current_upstream=$(<"$RUNTIME_DIR/active-upstream")
    current_container=$(<"$RUNTIME_DIR/active-container")
    current_slot=$(<"$RUNTIME_DIR/active-slot")
    current_commit=$(read_runtime_value active-commit)
    current_image=$(read_runtime_value active-image)
    previous_commit=$(read_runtime_value previous-commit)
    previous_image=$(read_runtime_value previous-image)
    case "$current_slot" in
        blue) previous_slot=green ;;
        *) previous_slot=blue ;;
    esac

    docker inspect "$previous_container" >/dev/null 2>&1 || die "上一版本容器不存在：$previous_container"
    docker start "$previous_container" >/dev/null
    wait_for_container_health "$previous_container" || die "上一版本容器健康检查失败：$previous_container"
    docker update --restart=unless-stopped "$previous_container" >/dev/null
    cp "$RUNTIME_DIR/Caddyfile" "$RUNTIME_DIR/Caddyfile.previous"
    write_caddy_upstream "$previous_upstream"
    if ! docker exec "$CADDY_CONTAINER" caddy validate --config /etc/caddy/Caddyfile; then
        restore_caddy_config
        die "回滚 Caddy 配置校验失败"
    fi
    docker exec "$CADDY_CONTAINER" caddy reload --config /etc/caddy/Caddyfile
    curl --fail --silent --show-error --max-time 10 "$PUBLIC_HEALTH_URL" >/dev/null || {
        restore_caddy_config
        docker exec "$CADDY_CONTAINER" caddy reload --config /etc/caddy/Caddyfile || true
        die "回滚后的公网健康检查失败，已恢复原上游"
    }
    docker update --restart=no "$current_container" >/dev/null 2>&1 || true
    docker stop --time 20 "$current_container" >/dev/null 2>&1 || true
    printf '%s\n' "$previous_upstream" > "$RUNTIME_DIR/active-upstream"
    printf '%s\n' "$previous_container" > "$RUNTIME_DIR/active-container"
    printf '%s\n' "$previous_slot" > "$RUNTIME_DIR/active-slot"
    printf '%s\n' "$current_upstream" > "$RUNTIME_DIR/previous-upstream"
    printf '%s\n' "$current_container" > "$RUNTIME_DIR/previous-container"
    [[ -n "$previous_commit" ]] && printf '%s\n' "$previous_commit" > "$RUNTIME_DIR/active-commit"
    [[ -n "$previous_image" ]] && printf '%s\n' "$previous_image" > "$RUNTIME_DIR/active-image"
    [[ -n "$current_commit" ]] && printf '%s\n' "$current_commit" > "$RUNTIME_DIR/previous-commit"
    [[ -n "$current_image" ]] && printf '%s\n' "$current_image" > "$RUNTIME_DIR/previous-image"
    log "已回滚到 $previous_upstream"
    exit 0
fi

target_commit="${1:-main}"
target_image="${2:-}"
[[ -n "$target_image" ]] || die "缺少 GHCR 镜像摘要参数"
is_immutable_ghcr_image "$target_image" || die "镜像必须使用 GHCR 不可变摘要：$target_image"
[[ "$target_image" == "$IMAGE_REPOSITORY"@sha256:* ]] || die "镜像仓库不匹配：$target_image"

[[ -d "$SOURCE_DIR/.git" ]] || die "服务器 Git 工作区不存在：$SOURCE_DIR"
log "开始部署目标：$target_commit"
git_source fetch --prune origin main
if [[ "$target_commit" == "main" ]]; then
    target_commit=origin/main
fi
git_source cat-file -e "$target_commit^{commit}" || die "目标提交不存在：$target_commit"
git_source checkout --detach --force "$target_commit"
resolved_commit=$(git_source rev-parse HEAD)
short_commit=${resolved_commit:0:12}

active_commit=$(read_runtime_value active-commit)
needs_server_deploy() {
    local current_commit="$1"
    if [[ -z "$current_commit" ]]; then
        log "服务器没有记录活动提交，将执行部署"
        return 0
    fi
    if ! git_source cat-file -e "$current_commit^{commit}" >/dev/null 2>&1; then
        log "服务器活动提交不可解析：$current_commit，将执行部署"
        return 0
    fi
    if ! git_source diff --quiet "$current_commit" "$resolved_commit" -- server compose.production.yaml deploy infra .github/workflows/server-deploy.yml; then
        log "检测到活动提交与目标提交之间存在服务端路径差异，将执行部署"
        return 0
    fi
    log "活动提交与目标提交的服务端路径一致，跳过部署"
    return 1
}
needs_server_deploy "$active_commit" || exit 0

if [[ ! -s "$RUNTIME_DIR/Caddyfile" ]]; then
    printf 'reverse_proxy api:8080\n' > "$RUNTIME_DIR/Caddyfile"
    printf 'api\n' > "$RUNTIME_DIR/active-upstream"
    printf 'plateview-api-1\n' > "$RUNTIME_DIR/active-container"
    printf 'legacy\n' > "$RUNTIME_DIR/active-slot"
fi
compose config -q
preflight_checks
record_resource_snapshot "部署前"

: "${POSTGRES_DB:?生产环境缺少 POSTGRES_DB}"
: "${POSTGRES_USER:?生产环境缺少 POSTGRES_USER}"
: "${POSTGRES_PASSWORD:?生产环境缺少 POSTGRES_PASSWORD}"

compose up -d --no-recreate postgres caddy
docker update --memory 384m --memory-reservation 256m --memory-swap 512m --cpus 0.45 --pids-limit 256 "$POSTGRES_CONTAINER" >/dev/null
docker update --memory 128m --memory-reservation 64m --memory-swap 192m --cpus 0.20 --pids-limit 128 "$CADDY_CONTAINER" >/dev/null
[[ "$(docker inspect -f '{{.State.Health.Status}}' "$POSTGRES_CONTAINER" 2>/dev/null || true)" == "healthy" ]] \
    || die "PostgreSQL 健康检查未通过"
[[ "$(docker inspect -f '{{.State.Running}}' "$CADDY_CONTAINER" 2>/dev/null || true)" == "true" ]] \
    || die "Caddy 未运行"

migration_changes=false
if [[ -z "$active_commit" ]] || ! git_source cat-file -e "$active_commit^{commit}" >/dev/null 2>&1; then
    migration_changes=true
elif [[ -n "$(git_source diff --name-only "$active_commit" "$resolved_commit" -- server/src/main/resources/db/migration)" ]]; then
    migration_changes=true
fi

if deployment_requires_backup "$active_commit" "$migration_changes"; then
    timestamp=$(date -u +%Y%m%dT%H%M%SZ)
    backup_file="$BACKUP_DIR/plateview-pre-${short_commit}-${timestamp}.dump"
    log "检测到数据库迁移变更，创建部署前备份：$backup_file"
    docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
        nice -n 15 pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$backup_file"
    sha256sum "$backup_file"
else
    log "本次没有数据库迁移变更，跳过额外完整备份"
fi
find "$BACKUP_DIR" -type f -name 'plateview-*.dump' -mtime +"$RETENTION_DAYS" -delete

log "拉取不可变候选镜像：$target_image"
docker pull "$target_image"
candidate="plateview-api-${short_commit}"
active_upstream=$(cat "$RUNTIME_DIR/active-upstream" 2>/dev/null || printf 'api')
active_container=$(cat "$RUNTIME_DIR/active-container" 2>/dev/null || printf 'plateview-api-1')
active_slot=$(cat "$RUNTIME_DIR/active-slot" 2>/dev/null || printf 'legacy')
case "$active_slot" in
    blue) next_slot=green ;;
    *) next_slot=blue ;;
esac
next_upstream="api-$next_slot"
failure_log="$LOG_DIR/candidate-${short_commit}-failed.log"

cleanup_candidate() {
    if docker inspect "$candidate" >/dev/null 2>&1; then
        docker inspect "$candidate" > "${failure_log}.inspect.json" 2>/dev/null || true
        docker logs "$candidate" > "$failure_log" 2>&1 || true
        docker rm -f "$candidate" >/dev/null 2>&1 || true
        log "候选容器失败日志已保存：$failure_log"
    fi
}
if docker inspect "$candidate" >/dev/null 2>&1; then
    [[ "$candidate" != "$active_container" ]] || die "目标提交已经是活动容器：$candidate"
    log "清理上次失败遗留的候选容器：$candidate"
    docker rm -f "$candidate" >/dev/null
fi
trap cleanup_candidate EXIT

log "启动受限候选容器：$candidate（槽位 $next_slot）"
docker run -d --name "$candidate" --restart=no \
    --memory "$API_MEMORY_LIMIT" \
    --memory-reservation "$API_MEMORY_RESERVATION" \
    --memory-swap "$API_MEMORY_SWAP_LIMIT" \
    --cpus "$API_CPU_LIMIT" \
    --pids-limit "$API_PIDS_LIMIT" \
    --log-opt max-size=20m \
    --log-opt max-file=3 \
    --env-file "$ENV_FILE" \
    -e WORK_ORDER_IMAGE_DIR=/opt/plateview/work-order-images \
    -v "$WORK_ORDER_IMAGE_DIR:/opt/plateview/work-order-images" \
    -e PORT=8080 \
    -e DATABASE_MIGRATE_ON_START=true \
    -e DATABASE_URL="jdbc:postgresql://postgres:5432/$POSTGRES_DB" \
    -e DATABASE_USERNAME="$POSTGRES_USER" \
    -e DATABASE_PASSWORD="$POSTGRES_PASSWORD" \
    -e JAVA_TOOL_OPTIONS="$API_JAVA_OPTIONS" \
    --network "$BACKEND_NETWORK" \
    --network-alias "$next_upstream" \
    "$target_image" >/dev/null
docker network connect "$EDGE_NETWORK" "$candidate"

wait_for_container_health "$candidate" || die "候选容器健康检查失败"
docker logs --tail 120 "$candidate"
verify_database_migrations

cp "$RUNTIME_DIR/Caddyfile" "$RUNTIME_DIR/Caddyfile.previous"
write_caddy_upstream "$next_upstream"
if ! docker exec "$CADDY_CONTAINER" caddy validate --config /etc/caddy/Caddyfile; then
    restore_caddy_config
    die "Caddy 新配置校验失败"
fi
docker exec "$CADDY_CONTAINER" caddy reload --config /etc/caddy/Caddyfile
sleep 2
if ! curl --fail --silent --show-error --max-time 10 "$PUBLIC_HEALTH_URL" >/dev/null; then
    restore_caddy_config
    docker exec "$CADDY_CONTAINER" caddy reload --config /etc/caddy/Caddyfile || true
    die "切流后的公网健康检查失败，已恢复旧上游"
fi

docker update --restart=unless-stopped "$candidate" >/dev/null
previous_image=$(read_runtime_value active-image)
printf '%s\n' "$active_upstream" > "$RUNTIME_DIR/previous-upstream"
printf '%s\n' "$active_container" > "$RUNTIME_DIR/previous-container"
[[ -n "$active_commit" ]] && printf '%s\n' "$active_commit" > "$RUNTIME_DIR/previous-commit"
[[ -n "$previous_image" ]] && printf '%s\n' "$previous_image" > "$RUNTIME_DIR/previous-image"
printf '%s\n' "$next_upstream" > "$RUNTIME_DIR/active-upstream"
printf '%s\n' "$candidate" > "$RUNTIME_DIR/active-container"
printf '%s\n' "$next_slot" > "$RUNTIME_DIR/active-slot"
printf '%s\n' "$resolved_commit" > "$RUNTIME_DIR/active-commit"
printf '%s\n' "$target_image" > "$RUNTIME_DIR/active-image"

if docker inspect "$active_container" >/dev/null 2>&1 && [[ "$active_container" != "$candidate" ]]; then
    docker update --restart=no "$active_container" >/dev/null 2>&1 || true
    docker stop --time 20 "$active_container" >/dev/null 2>&1 || true
fi
trap - EXIT
if ! bash "$API_CONTAINER_RETENTION_SCRIPT" "$candidate" "$active_container"; then
    log "警告：API 容器保留清理未完整执行，当前部署仍可用"
fi
if ! bash "$API_IMAGE_RETENTION_SCRIPT" "$candidate" "$active_container" "$IMAGE_REPOSITORY"; then
    log "警告：API 镜像保留清理未完整执行，当前部署仍可用"
fi
if ! bash "$API_IMAGE_RETENTION_SCRIPT" "$candidate" "$active_container" plateview-api; then
    log "警告：旧版本地 API 镜像保留清理未完整执行，当前部署仍可用"
fi
record_resource_snapshot "部署后"
log "部署成功：提交 $resolved_commit，镜像 $target_image，活动槽位 $next_slot，上游 $next_upstream"
