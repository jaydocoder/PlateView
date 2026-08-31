#!/usr/bin/env bash
set -Eeuo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly MIRROR_SCRIPT="$PROJECT_ROOT/deploy/plateview-update-mirror.sh"
readonly TEMP_DIR="$(mktemp -d)"
readonly PORT="$((20000 + RANDOM % 10000))"

cleanup() {
    if [[ -n "${SERVER_PID:-}" ]]; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

fixture_dir="$TEMP_DIR/fixture"
app_dir="$TEMP_DIR/app"
mkdir -p "$fixture_dir/repos/jaydocoder/PlateView/releases" \
    "$fixture_dir/releases/download/v0.9.9"
printf '{"tag_name":"v0.9.9"}\n' > "$fixture_dir/repos/jaydocoder/PlateView/releases/latest"
dd if=/dev/zero of="$fixture_dir/releases/download/v0.9.9/app-release.apk" bs=1024 count=16 status=none

python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$fixture_dir" >"$TEMP_DIR/http.log" 2>&1 &
SERVER_PID=$!
for _ in $(seq 1 20); do
    curl --fail --silent "http://127.0.0.1:$PORT/repos/jaydocoder/PlateView/releases/latest" >/dev/null && break
    sleep 0.1
done

PLATEVIEW_APP_DIR="$app_dir" \
PLATEVIEW_RELEASE_API_URL="http://127.0.0.1:$PORT/repos/jaydocoder/PlateView/releases/latest" \
PLATEVIEW_RELEASE_DOWNLOAD_BASE_URL="http://127.0.0.1:$PORT/releases/download" \
    "$MIRROR_SCRIPT"

apk_file="$app_dir/updates/PlateView-v0.9.9.apk"
latest_file="$app_dir/updates/latest.json"
expected_sha=$(sha256sum "$fixture_dir/releases/download/v0.9.9/app-release.apk" | awk '{print $1}')
actual_sha=$(sha256sum "$apk_file" | awk '{print $1}')
[[ "$actual_sha" == "$expected_sha" ]]
grep -F "\"versionName\":\"v0.9.9\"" "$latest_file" >/dev/null
grep -F "\"sha256\":\"$expected_sha\"" "$latest_file" >/dev/null

second_run_output=$(PLATEVIEW_APP_DIR="$app_dir" \
    PLATEVIEW_RELEASE_API_URL="http://127.0.0.1:$PORT/repos/jaydocoder/PlateView/releases/latest" \
    PLATEVIEW_RELEASE_DOWNLOAD_BASE_URL="http://127.0.0.1:$PORT/releases/download" \
    "$MIRROR_SCRIPT")
grep -F "镜像已是最新版本：v0.9.9" <<<"$second_run_output" >/dev/null
printf '镜像同步测试通过\n'
