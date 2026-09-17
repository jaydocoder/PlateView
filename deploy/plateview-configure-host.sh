#!/usr/bin/env bash
set -Eeuo pipefail

readonly SWAP_FILE="${PLATEVIEW_SWAP_FILE:-/swapfile}"
readonly SWAP_SIZE_MIB="${PLATEVIEW_SWAP_SIZE_MIB:-1024}"
readonly SYSCTL_FILE="/etc/sysctl.d/99-plateview-memory.conf"

[[ "$(id -u)" == "0" ]] || { printf '必须使用 root 配置生产宿主机\n' >&2; exit 1; }
[[ "$SWAP_FILE" =~ ^/[^[:space:]]+$ && "$SWAP_FILE" != "/" ]] \
    || { printf '交换文件路径无效：%s\n' "$SWAP_FILE" >&2; exit 1; }
[[ "$SWAP_SIZE_MIB" =~ ^[0-9]+$ && "$SWAP_SIZE_MIB" -ge 1024 ]] \
    || { printf '交换空间必须至少为 1024MiB\n' >&2; exit 1; }

if ! swapon --show=NAME --noheadings | awk '{$1=$1; print}' | grep -Fxq "$SWAP_FILE"; then
    swapoff "$SWAP_FILE" >/dev/null 2>&1 || true
    dd if=/dev/zero of="$SWAP_FILE" bs=1M count="$SWAP_SIZE_MIB" status=none
    chmod 0600 "$SWAP_FILE"
    mkswap "$SWAP_FILE" >/dev/null
    swapon "$SWAP_FILE"
fi

awk -v swap_file="$SWAP_FILE" '$1 == swap_file { found = 1 } END { exit !found }' /etc/fstab \
    || printf '%s none swap sw 0 0\n' "$SWAP_FILE" >> /etc/fstab
printf 'vm.swappiness=10\n' > "$SYSCTL_FILE"
sysctl -p "$SYSCTL_FILE" >/dev/null

printf '生产宿主机内存保护已配置\n'
swapon --show
sysctl vm.swappiness
