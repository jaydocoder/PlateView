#!/usr/bin/env bash
set -Eeuo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=deploy/plateview-deploy-lib.sh
source "$PROJECT_ROOT/deploy/plateview-deploy-lib.sh"

integer_is_at_least 2097152 2097152
integer_is_at_least 2097153 2097152
if integer_is_at_least 1024 2097152; then
    printf '磁盘低于阈值时不应通过\n' >&2
    exit 1
fi

decimal_is_not_greater_than 0.75 1.2
decimal_is_not_greater_than 1.2 1.2
if decimal_is_not_greater_than 1.21 1.2; then
    printf '负载高于阈值时不应通过\n' >&2
    exit 1
fi

valid_image='ghcr.io/jaydocoder/plateview-api@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
is_immutable_ghcr_image "$valid_image"
if is_immutable_ghcr_image 'ghcr.io/jaydocoder/plateview-api:main'; then
    printf '可变镜像标签不应通过\n' >&2
    exit 1
fi

deployment_requires_backup '' false
deployment_requires_backup abc true
if deployment_requires_backup abc false; then
    printf '已有活动提交且迁移未变化时不应额外备份\n' >&2
    exit 1
fi

if grep -F 'docker build' "$PROJECT_ROOT/deploy/plateview-deploy.sh" >/dev/null; then
    printf '生产部署脚本不得执行 Docker 构建\n' >&2
    exit 1
fi
grep -F 'install -d -m 0750 -o "$deployment_owner" -g "$deployment_group" "$WORK_ORDER_IMAGE_DIR"' \
    "$PROJECT_ROOT/deploy/install-low-pressure-host.sh" >/dev/null
grep -F '微信车单附件目录不可创建，请先使用 root 执行 install-low-pressure-host.sh' \
    "$PROJECT_ROOT/deploy/plateview-deploy.sh" >/dev/null
grep -F '微信车单附件目录不可写' "$PROJECT_ROOT/deploy/plateview-deploy.sh" >/dev/null
[[ -f "$PROJECT_ROOT/server/src/main/resources/db/migration/V30__restrict_user_vehicle_data_access_defaults.sql" ]]
grep -F 'docker/build-push-action@v6' "$PROJECT_ROOT/.github/workflows/server-deploy.yml" >/dev/null
grep -F 'TARGET_IMAGE' "$PROJECT_ROOT/.github/workflows/server-deploy.yml" >/dev/null
grep -F 'OnCalendar=*-*-* 19:30:00' "$PROJECT_ROOT/deploy/systemd/plateview-database-backup.timer" >/dev/null
grep -F 'AccuracySec=15min' "$PROJECT_ROOT/deploy/systemd/plateview-database-backup.timer" >/dev/null
if grep -E 'RandomizedDelaySec|OnCalendar=.* UTC' \
    "$PROJECT_ROOT/deploy/systemd/plateview-database-backup.timer" >/dev/null; then
    printf '数据库备份定时器不得使用 systemd 219 不兼容的配置\n' >&2
    exit 1
fi

printf '低压力部署策略测试通过\n'
