#!/usr/bin/env bash
set -Eeuo pipefail

readonly APP_DIR="${PLATEVIEW_APP_DIR:-/opt/plateview}"
readonly SOURCE_DIR="$APP_DIR/source"
readonly ENV_FILE="$APP_DIR/.env"
readonly RUNTIME_DIR="$APP_DIR/runtime"
readonly BACKUP_DIR="$APP_DIR/backups"
readonly LOG_DIR="$APP_DIR/logs/deploy"
readonly LOCK_FILE="$RUNTIME_DIR/deploy.lock"
readonly COMPOSE_FILE="$SOURCE_DIR/compose.production.yaml"
readonly CADDY_CONTAINER="${PLATEVIEW_CADDY_CONTAINER:-plateview-caddy-1}"
readonly POSTGRES_CONTAINER="${PLATEVIEW_POSTGRES_CONTAINER:-plateview-postgres-1}"
readonly BACKEND_NETWORK="${PLATEVIEW_BACKEND_NETWORK:-plateview_backend}"
readonly EDGE_NETWORK="${PLATEVIEW_EDGE_NETWORK:-plateview_edge}"
readonly PUBLIC_HEALTH_URL="${PLATEVIEW_PUBLIC_HEALTH_URL:-https://api.chenxiruyu.dpdns.org/health}"
readonly RETENTION_DAYS="${PLATEVIEW_BACKUP_RETENTION_DAYS:-14}"

mkdir -p "$RUNTIME_DIR" "$BACKUP_DIR" "$LOG_DIR"
readonly LOG_FILE="$LOG_DIR/deploy-$(date -u +%Y%m%dT%H%M%SZ)-${1:-manual}.log"
exec > >(tee -a "$LOG_FILE") 2>&1

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() { log "失败：$*"; exit 1; }
compose() { docker compose --project-directory "$APP_DIR" --project-name plateview --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }
git_source() { git --git-dir="$SOURCE_DIR/.git" --work-tree="$SOURCE_DIR" "$@"; }

exec 9>"$LOCK_FILE"
flock -n 9 || die "已有部署正在运行，拒绝并发部署"

if [[ "${1:-}" == "rollback" ]]; then
    [[ -s "$RUNTIME_DIR/previous-upstream" ]] || die "没有可用的上一版本上游"
    [[ -s "$RUNTIME_DIR/previous-container" ]] || die "没有可用的上一版本容器"
    previous_upstream=$(<"$RUNTIME_DIR/previous-upstream")
    previous_container=$(<"$RUNTIME_DIR/previous-container")
    docker inspect "$previous_container" >/dev/null 2>&1 || die "上一版本容器不存在：$previous_container"
    printf 'reverse_proxy %s:8080\n' "$previous_upstream" > "$RUNTIME_DIR/Caddyfile.next"
    cp "$RUNTIME_DIR/Caddyfile" "$RUNTIME_DIR/Caddyfile.previous"
    mv "$RUNTIME_DIR/Caddyfile.next" "$RUNTIME_DIR/Caddyfile"
    if ! docker exec "$CADDY_CONTAINER" caddy validate --config /etc/caddy/Caddyfile; then
        mv "$RUNTIME_DIR/Caddyfile.previous" "$RUNTIME_DIR/Caddyfile"
        die "回滚 Caddy 配置校验失败"
    fi
    docker exec "$CADDY_CONTAINER" caddy reload --config /etc/caddy/Caddyfile
    curl --fail --silent --show-error --max-time 10 "$PUBLIC_HEALTH_URL" >/dev/null
    printf '%s\n' "$previous_upstream" > "$RUNTIME_DIR/active-upstream"
    printf '%s\n' "$previous_container" > "$RUNTIME_DIR/active-container"
    log "已回滚到 $previous_upstream"
    exit 0
fi

target_commit="${1:-main}"
if [[ "$target_commit" == "main" ]]; then
    target_commit=origin/main
fi
[[ -d "$SOURCE_DIR/.git" ]] || die "服务器 Git 工作区不存在：$SOURCE_DIR"
[[ -r "$ENV_FILE" ]] || die "生产环境文件不存在：$ENV_FILE"

log "开始部署目标：$target_commit"
git_source fetch --prune origin main
git_source cat-file -e "$target_commit^{commit}" || die "目标提交不存在：$target_commit"
git_source checkout --detach --force "$target_commit"
resolved_commit=$(git_source rev-parse HEAD)
short_commit=${resolved_commit:0:12}

if git_source rev-parse "$resolved_commit^" >/dev/null 2>&1; then
    has_server_change=0
    while IFS= read -r changed_path; do
        case "$changed_path" in
            server/*|compose.production.yaml|deploy/*|infra/*) has_server_change=1 ;;
        esac
    done < <(git_source diff-tree --no-commit-id --name-only -r "$resolved_commit")
    [[ "$has_server_change" == 1 ]] || { log "提交没有服务端路径变化，跳过部署"; exit 0; }
fi

if [[ ! -s "$RUNTIME_DIR/Caddyfile" ]]; then
    printf 'reverse_proxy api:8080\n' > "$RUNTIME_DIR/Caddyfile"
    printf 'api\n' > "$RUNTIME_DIR/active-upstream"
    printf 'plateview-api-1\n' > "$RUNTIME_DIR/active-container"
    printf 'legacy\n' > "$RUNTIME_DIR/active-slot"
fi
compose config -q

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a
: "${POSTGRES_DB:?生产环境缺少 POSTGRES_DB}"
: "${POSTGRES_USER:?生产环境缺少 POSTGRES_USER}"
: "${POSTGRES_PASSWORD:?生产环境缺少 POSTGRES_PASSWORD}"

compose up -d postgres caddy
compose ps

verify_database_migrations() {
    local expected_version applied_version failed_migrations
    expected_version=$(find "$SOURCE_DIR/server/src/main/resources/db/migration" -maxdepth 1 -type f -name 'V*__*.sql' -printf '%f\n' \
        | sed -E 's/^V([0-9]+)__.*/\1/' \
        | sort -n \
        | tail -n 1)
    [[ -n "$expected_version" ]] || die "未找到 Flyway 迁移脚本"

    failed_migrations=$(docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
        psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc \
        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE;")
    [[ "$failed_migrations" == "0" ]] || die "Flyway 存在 $failed_migrations 条失败迁移记录"

    applied_version=$(docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
        psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc \
        "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1;")
    [[ "$applied_version" == "$expected_version" ]] || die "Flyway 迁移版本不一致：期望 V$expected_version，实际 V${applied_version:-无}"
    log "Flyway 迁移校验通过：V$applied_version，失败记录 $failed_migrations 条"
}

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_file="$BACKUP_DIR/plateview-pre-${short_commit}-${timestamp}.dump"
log "创建数据库备份：$backup_file"
docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" plateview-postgres-1 \
    pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$backup_file"
sha256sum "$backup_file"
find "$BACKUP_DIR" -type f -name 'plateview-*.dump' -mtime +"$RETENTION_DAYS" -delete

image_tag="plateview-api:$resolved_commit"
candidate="plateview-api-${short_commit}"
active_upstream=$(cat "$RUNTIME_DIR/active-upstream" 2>/dev/null || printf 'api')
active_container=$(cat "$RUNTIME_DIR/active-container" 2>/dev/null || printf 'plateview-api-1')
active_slot=$(cat "$RUNTIME_DIR/active-slot" 2>/dev/null || printf 'legacy')
case "$active_slot" in
    blue) next_slot=green ;;
    *) next_slot=blue ;;
esac
next_upstream="api-$next_slot"

cleanup_candidate() {
    if docker inspect "$candidate" >/dev/null 2>&1; then
        docker rm -f "$candidate" >/dev/null 2>&1 || true
    fi
}
trap cleanup_candidate ERR

log "构建镜像：$image_tag"
DOCKER_BUILDKIT=1 docker build -t "$image_tag" "$SOURCE_DIR/server"
log "启动候选容器：$candidate（槽位 $next_slot）"
docker run -d --name "$candidate" --restart=no \
    --env-file "$ENV_FILE" \
    -e PORT=8080 \
    -e DATABASE_MIGRATE_ON_START=true \
    -e DATABASE_URL="jdbc:postgresql://postgres:5432/$POSTGRES_DB" \
    -e DATABASE_USERNAME="$POSTGRES_USER" \
    -e DATABASE_PASSWORD="$POSTGRES_PASSWORD" \
    --network "$BACKEND_NETWORK" \
    --network-alias "$next_upstream" \
    "$image_tag" >/dev/null
docker network connect "$EDGE_NETWORK" "$candidate"

candidate_ip=$(docker inspect -f "{{with index .NetworkSettings.Networks \"$BACKEND_NETWORK\"}}{{.IPAddress}}{{end}}" "$candidate")
[[ -n "$candidate_ip" ]] || die "无法取得候选容器地址"
healthy=0
for _ in $(seq 1 30); do
    if curl --fail --silent --show-error --max-time 2 "http://$candidate_ip:8080/health" >/dev/null; then
        healthy=1
        break
    fi
    sleep 2
done
[[ "$healthy" == 1 ]] || die "候选容器健康检查失败"
docker logs --tail 120 "$candidate"
verify_database_migrations

printf 'reverse_proxy %s:8080\n' "$next_upstream" > "$RUNTIME_DIR/Caddyfile.next"
cp "$RUNTIME_DIR/Caddyfile" "$RUNTIME_DIR/Caddyfile.previous"
mv "$RUNTIME_DIR/Caddyfile.next" "$RUNTIME_DIR/Caddyfile"
if ! docker exec "$CADDY_CONTAINER" caddy validate --config /etc/caddy/Caddyfile; then
    mv "$RUNTIME_DIR/Caddyfile.previous" "$RUNTIME_DIR/Caddyfile"
    die "Caddy 新配置校验失败"
fi
docker exec "$CADDY_CONTAINER" caddy reload --config /etc/caddy/Caddyfile
sleep 2
if ! curl --fail --silent --show-error --max-time 10 "$PUBLIC_HEALTH_URL" >/dev/null; then
    mv "$RUNTIME_DIR/Caddyfile.previous" "$RUNTIME_DIR/Caddyfile"
    docker exec "$CADDY_CONTAINER" caddy reload --config /etc/caddy/Caddyfile || true
    die "切流后的公网健康检查失败，已恢复旧上游"
fi

printf '%s\n' "$active_upstream" > "$RUNTIME_DIR/previous-upstream"
printf '%s\n' "$active_container" > "$RUNTIME_DIR/previous-container"
printf '%s\n' "$next_upstream" > "$RUNTIME_DIR/active-upstream"
printf '%s\n' "$candidate" > "$RUNTIME_DIR/active-container"
printf '%s\n' "$next_slot" > "$RUNTIME_DIR/active-slot"
printf '%s\n' "$resolved_commit" > "$RUNTIME_DIR/active-commit"
trap - ERR
log "部署成功：提交 $resolved_commit，活动槽位 $next_slot，上游 $next_upstream"
