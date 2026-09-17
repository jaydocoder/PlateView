#!/usr/bin/env bash
set -Eeuo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly DEPLOY_LIBRARY="$PROJECT_ROOT/deploy/plateview-deploy-lib.sh"

source "$DEPLOY_LIBRARY"

migration_version_is_at_least 29 29
migration_version_is_at_least 29 30

if migration_version_is_at_least 30 29; then
    printf '数据库版本低于源码要求时不应通过校验\n' >&2
    exit 1
fi

if migration_version_is_at_least 29 ''; then
    printf '缺失数据库版本时不应通过校验\n' >&2
    exit 1
fi

printf 'Flyway 迁移版本校验测试通过\n'
