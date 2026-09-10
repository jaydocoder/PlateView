#!/usr/bin/env bash
set -Eeuo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly RETENTION_SCRIPT="$PROJECT_ROOT/deploy/plateview-retain-api-containers.sh"

(
    removed_containers=()
    docker() {
        case "$1" in
            ps)
                printf '%s\n' \
                    plateview-api-current \
                    plateview-api-previous \
                    plateview-api-stale \
                    plateview-api-1
                ;;
            rm)
                removed_containers+=("$3")
                ;;
            *)
                printf '未预期的 Docker 调用：%s\n' "$*" >&2
                return 1
                ;;
        esac
    }

    source "$RETENTION_SCRIPT" plateview-api-current plateview-api-previous
    [[ "${removed_containers[*]}" == "plateview-api-stale plateview-api-1" ]]
)

(
    docker() {
        case "$1" in
            ps)
                printf '%s\n' plateview-api-current plateview-api-previous plateview-api-stale
                ;;
            rm)
                [[ "$3" == plateview-api-stale ]]
                return 1
                ;;
            *)
                return 1
                ;;
        esac
    }

    source "$RETENTION_SCRIPT" plateview-api-current plateview-api-previous
)

printf 'API 容器保留测试通过\n'
