#!/usr/bin/env bash
set -Eeuo pipefail

readonly ACTIVE_CONTAINER="${1:?缺少活动 API 容器名称}"
readonly PREVIOUS_CONTAINER="${2:?缺少上一版本 API 容器名称}"
readonly IMAGE_REPOSITORY="${3:-ghcr.io/jaydocoder/plateview-api}"

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

[[ "$ACTIVE_CONTAINER" == plateview-api-* ]] || {
    log "拒绝清理：活动容器名称无效：$ACTIVE_CONTAINER" >&2
    exit 1
}
[[ "$PREVIOUS_CONTAINER" == plateview-api-* ]] || {
    log "拒绝清理：上一版本容器名称无效：$PREVIOUS_CONTAINER" >&2
    exit 1
}

declare -A retained_image_ids=()
for container in "$ACTIVE_CONTAINER" "$PREVIOUS_CONTAINER"; do
    image_id=$(docker inspect --format '{{.Image}}' "$container")
    retained_image_ids["$image_id"]=1
done

declare -A attempted_image_ids=()
api_images=$(docker image ls "$IMAGE_REPOSITORY" --no-trunc --format '{{.ID}}')
while read -r image_id; do
    [[ -n "$image_id" ]] || continue
    [[ -n "${attempted_image_ids[$image_id]:-}" ]] && continue
    attempted_image_ids["$image_id"]=1
    [[ -n "${retained_image_ids[$image_id]:-}" ]] && continue

    if docker image rm "$image_id" >/dev/null; then
        log "已清理过期 API 镜像：$image_id"
    else
        log "警告：未能清理过期 API 镜像：$image_id" >&2
    fi
done <<<"$api_images"
