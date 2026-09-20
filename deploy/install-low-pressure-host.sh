#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_DIR="${PLATEVIEW_SOURCE_DIR:-/opt/plateview/source}"
readonly APP_DIR="${PLATEVIEW_APP_DIR:-/opt/plateview}"
readonly WORK_ORDER_IMAGE_DIR="$APP_DIR/data/work-order-images"

[[ "$(id -u)" == "0" ]] || { printf '必须使用 root 安装低压力部署配置\n' >&2; exit 1; }
[[ -d "$SOURCE_DIR" ]] || { printf '源码目录不存在：%s\n' "$SOURCE_DIR" >&2; exit 1; }
deployment_owner=$(stat -c '%U' "$SOURCE_DIR")
deployment_group=$(stat -c '%G' "$SOURCE_DIR")
install -d -m 0750 -o "$deployment_owner" -g "$deployment_group" "$WORK_ORDER_IMAGE_DIR"
"$SOURCE_DIR/deploy/plateview-configure-host.sh"
install -D -m 0644 "$SOURCE_DIR/deploy/systemd/plateview-database-backup.service" \
    /etc/systemd/system/plateview-database-backup.service
install -D -m 0644 "$SOURCE_DIR/deploy/systemd/plateview-database-backup.timer" \
    /etc/systemd/system/plateview-database-backup.timer
systemctl daemon-reload
systemctl enable --now plateview-database-backup.timer
systemctl status plateview-database-backup.timer --no-pager
