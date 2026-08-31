#!/usr/bin/env bash
set -Eeuo pipefail

readonly APP_DIR="${PLATEVIEW_APP_DIR:-/opt/plateview}"
readonly UPDATES_DIR="$APP_DIR/updates"
readonly RUNTIME_DIR="$APP_DIR/runtime"
readonly LOCK_FILE="$RUNTIME_DIR/update-mirror.lock"
readonly RELEASE_API_URL="${PLATEVIEW_RELEASE_API_URL:-https://api.github.com/repos/jaydocoder/PlateView/releases/latest}"
readonly RELEASE_DOWNLOAD_BASE_URL="${PLATEVIEW_RELEASE_DOWNLOAD_BASE_URL:-https://github.com/jaydocoder/PlateView/releases/download}"
readonly RELEASE_ASSET_NAME="${PLATEVIEW_RELEASE_ASSET_NAME:-app-release.apk}"
readonly RETAIN_COUNT="${PLATEVIEW_UPDATE_RETAIN_COUNT:-5}"

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() { log "失败：$*"; exit 1; }

mkdir -p "$UPDATES_DIR" "$RUNTIME_DIR"
exec 9>"$LOCK_FILE"
flock -n 9 || { log "已有镜像同步正在运行，跳过本次执行"; exit 0; }

release_json=$(curl --fail --silent --show-error --location --retry 3 \
    --connect-timeout 20 --max-time 60 \
    -H 'Accept: application/vnd.github+json' \
    "$RELEASE_API_URL")
tag=$(printf '%s' "$release_json" | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "GitHub Release 返回了无效版本标签：${tag:-空}"

apk_file="$UPDATES_DIR/PlateView-${tag}.apk"
part_file="$UPDATES_DIR/.PlateView-${tag}.apk.part"
latest_file="$UPDATES_DIR/latest.json"

if [[ -f "$apk_file" && -f "$latest_file" ]]; then
    current_tag=$(sed -n 's/.*"versionName":"\([^"]*\)".*/\1/p' "$latest_file")
    expected_sha=$(sed -n 's/.*"sha256":"\([a-f0-9]\{64\}\)".*/\1/p' "$latest_file")
    actual_sha=$(sha256sum "$apk_file" | awk '{print $1}')
    if [[ "$current_tag" == "$tag" && "$expected_sha" == "$actual_sha" ]]; then
        log "镜像已是最新版本：$tag"
        exit 0
    fi
fi

download_url="${RELEASE_DOWNLOAD_BASE_URL%/}/${tag}/${RELEASE_ASSET_NAME}"
log "开始同步 GitHub Release：$tag"
curl --fail --show-error --location --continue-at - --retry 3 \
    --connect-timeout 20 --max-time 3600 --output "$part_file" "$download_url"

sha256=$(sha256sum "$part_file" | awk '{print $1}')
[[ "$sha256" =~ ^[a-f0-9]{64}$ ]] || die "APK SHA-256 无效"
mv "$part_file" "$apk_file"
chmod 0644 "$apk_file"

printf '{"versionName":"%s","releaseNotes":"","apkUrl":"https://api.chenxiruyu.dpdns.org/updates/PlateView-%s.apk","sha256":"%s"}\n' \
    "$tag" "$tag" "$sha256" > "${latest_file}.next"
mv "${latest_file}.next" "$latest_file"

mapfile -t expired_files < <(find "$UPDATES_DIR" -maxdepth 1 -type f -name 'PlateView-v*.apk' -printf '%T@ %p\n' \
    | sort -nr | tail -n +$((RETAIN_COUNT + 1)) | cut -d' ' -f2-)
for expired_file in "${expired_files[@]}"; do
    [[ -n "$expired_file" ]] && rm -f "$expired_file"
done

log "镜像同步完成：$tag，SHA-256：$sha256"
