#!/usr/bin/env bash
set -Eeuo pipefail

readonly ACTIVE_CONTAINER="${1:?缺少活动 API 容器名称}"
readonly PREVIOUS_CONTAINER="${2:?缺少上一版本 API 容器名称}"

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

[[ "$ACTIVE_CONTAINER" == plateview-api-* ]] || {
    log "拒绝清理：活动容器名称无效：$ACTIVE_CONTAINER" >&2
    exit 1
}
[[ "$PREVIOUS_CONTAINER" == plateview-api-* ]] || {
    log "拒绝清理：上一版本容器名称无效：$PREVIOUS_CONTAINER" >&2
    exit 1
}

api_containers=$(docker ps -a --filter 'name=^/plateview-api-' --format '{{.Names}}')
while IFS= read -r container; do
    [[ -n "$container" ]] || continue
    [[ "$container" == "$ACTIVE_CONTAINER" || "$container" == "$PREVIOUS_CONTAINER" ]] && continue

    if docker rm -f "$container" >/dev/null; then
        log "已清理过期 API 容器：$container"
    else
        log "警告：未能清理过期 API 容器：$container" >&2
    fi
done <<<"$api_containers"
