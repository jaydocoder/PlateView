#!/usr/bin/env bash
set -Eeuo pipefail
readonly BACKUP_FILE="${1:?用法：$0 <备份文件> <期望SHA-256>}"
readonly EXPECTED_SHA256="${2:?用法：$0 <备份文件> <期望SHA-256>}"
readonly BACKUP_DIR="${PLATEVIEW_BACKUP_DIR:-/opt/plateview/backups}"
readonly RUNTIME_DIR="${PLATEVIEW_RESTORE_RUNTIME_DIR:-/opt/plateview/restore-runtime}"
readonly REQUEST_ID="$(uuidgen 2>/dev/null || date +%s%N)"
readonly REQUEST_FILE="$RUNTIME_DIR/$REQUEST_ID.request"
readonly RESULT_FILE="$RUNTIME_DIR/$REQUEST_ID.result"
mkdir -p "$RUNTIME_DIR"
file="$(realpath -e "$BACKUP_FILE")"
root="$(realpath -e "$BACKUP_DIR")"
[[ "$file" == "$root"/* ]] || { printf '备份路径不在允许目录\n' >&2; exit 1; }
printf '%s|%s\n' "$(basename "$file")" "$EXPECTED_SHA256" > "$REQUEST_FILE.tmp"
mv "$REQUEST_FILE.tmp" "$REQUEST_FILE"
for _ in $(seq 1 1800); do
    if [[ -f "$RESULT_FILE" ]]; then
        code="$(head -n 1 "$RESULT_FILE")"
        tail -n +2 "$RESULT_FILE"
        rm -f "$RESULT_FILE"
        exit "$code"
    fi
    sleep 1
done
printf '数据库恢复执行器响应超时\n' >&2
exit 1
