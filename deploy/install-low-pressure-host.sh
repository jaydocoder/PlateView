#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_DIR="${PLATEVIEW_SOURCE_DIR:-/opt/plateview/source}"

[[ "$(id -u)" == "0" ]] || { printf '必须使用 root 安装低压力部署配置\n' >&2; exit 1; }
"$SOURCE_DIR/deploy/plateview-configure-host.sh"
install -D -m 0644 "$SOURCE_DIR/deploy/systemd/plateview-database-backup.service" \
    /etc/systemd/system/plateview-database-backup.service
install -D -m 0644 "$SOURCE_DIR/deploy/systemd/plateview-database-backup.timer" \
    /etc/systemd/system/plateview-database-backup.timer
systemctl daemon-reload
systemctl enable --now plateview-database-backup.timer
systemctl status plateview-database-backup.timer --no-pager
