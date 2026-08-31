#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_DIR="${PLATEVIEW_SOURCE_DIR:-/opt/plateview/source}"

[[ "$(id -u)" == "0" ]] || { printf '必须使用 root 安装 APK 镜像定时器\n' >&2; exit 1; }
install -D -m 0644 "$SOURCE_DIR/deploy/systemd/plateview-update-mirror.service" \
    /etc/systemd/system/plateview-update-mirror.service
install -D -m 0644 "$SOURCE_DIR/deploy/systemd/plateview-update-mirror.timer" \
    /etc/systemd/system/plateview-update-mirror.timer
systemctl daemon-reload
systemctl enable --now plateview-update-mirror.timer
systemctl start plateview-update-mirror.service
systemctl status plateview-update-mirror.timer --no-pager
