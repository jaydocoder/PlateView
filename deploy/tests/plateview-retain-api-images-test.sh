#!/usr/bin/env bash
set -Eeuo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly RETENTION_SCRIPT="$PROJECT_ROOT/deploy/plateview-retain-api-images.sh"

(
    removed_images=()
    docker() {
        case "$1 $2" in
            'inspect --format')
                case "$4" in
                    plateview-api-current) printf '%s\n' sha256:current ;;
                    plateview-api-previous) printf '%s\n' sha256:previous ;;
                    *) return 1 ;;
                esac
                ;;
            'image ls')
                printf '%s\n' \
                    'plateview-api:current sha256:current' \
                    'plateview-api:previous sha256:previous' \
                    'plateview-api:stale-one sha256:stale' \
                    'plateview-api:stale-two sha256:stale'
                ;;
            'image rm')
                removed_images+=("$3")
                ;;
            *)
                printf '未预期的 Docker 调用：%s\n' "$*" >&2
                return 1
                ;;
        esac
    }

    source "$RETENTION_SCRIPT" plateview-api-current plateview-api-previous
    [[ "${removed_images[*]}" == 'plateview-api:stale-one plateview-api:stale-two' ]]
)

(
    docker() {
        case "$1 $2" in
            'inspect --format')
                printf '%s\n' "sha256:${4#plateview-api-}"
                ;;
            'image ls')
                printf '%s\n' \
                    'plateview-api:current sha256:current' \
                    'plateview-api:previous sha256:previous' \
                    'plateview-api:stale sha256:stale'
                ;;
            'image rm')
                [[ "$3" == plateview-api:stale ]]
                return 1
                ;;
            *)
                return 1
                ;;
        esac
    }

    source "$RETENTION_SCRIPT" plateview-api-current plateview-api-previous
)

printf 'API 镜像保留测试通过\n'
