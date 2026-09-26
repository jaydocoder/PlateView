#!/bin/sh
set -eu
readonly RUNTIME_DIR="${PLATEVIEW_RESTORE_RUNTIME_DIR:-/opt/plateview/restore-runtime}"
readonly BACKUP_DIR="${PLATEVIEW_BACKUP_DIR:-/opt/plateview/backups}"
mkdir -p "$RUNTIME_DIR"
while true; do
    for request in "$RUNTIME_DIR"/*.request; do
        [ -f "$request" ] || continue
        id="$(basename "$request" .request)"
        result="$RUNTIME_DIR/$id.result"
        IFS='|' read -r backup sha256 < "$request"
        output_file="$RUNTIME_DIR/$id.output"
        set +e
        /bin/sh /opt/plateview/bin/plateview-restore-backup.sh "$BACKUP_DIR/$backup" "$sha256" > "$output_file" 2>&1
        code=$?
        set -e
        {
            printf '%s\n' "$code"
            cat "$output_file"
        } > "$result.tmp"
        mv "$result.tmp" "$result"
        rm -f "$request" "$output_file"
    done
    sleep 1
done
