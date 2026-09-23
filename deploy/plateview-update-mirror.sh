#!/usr/bin/env bash
set -Eeuo pipefail

readonly APP_DIR="${PLATEVIEW_APP_DIR:-/opt/plateview}"
readonly UPDATES_DIR="$APP_DIR/updates"
readonly RUNTIME_DIR="$APP_DIR/runtime"
readonly LOCK_FILE="$RUNTIME_DIR/update-mirror.lock"
readonly RELEASE_API_URL="${PLATEVIEW_RELEASE_API_URL:-https://api.github.com/repos/jaydocoder/PlateView/releases/latest}"
readonly RELEASE_DOWNLOAD_BASE_URL="${PLATEVIEW_RELEASE_DOWNLOAD_BASE_URL:-https://github.com/jaydocoder/PlateView/releases/download}"
readonly RETAIN_COUNT="${PLATEVIEW_UPDATE_RETAIN_COUNT:-5}"
readonly ARCHITECTURES=(arm64-v8a armeabi-v7a universal)

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

latest_file="$UPDATES_DIR/latest.json"

all_valid=false
if [[ -f "$latest_file" ]]; then
    current_tag=$(sed -n 's/.*"versionName":"\([^"]*\)".*/\1/p' "$latest_file")
    if [[ "$current_tag" == "$tag" ]]; then
        all_valid=true
        for architecture in "${ARCHITECTURES[@]}"; do
            [[ -s "$UPDATES_DIR/PlateView-${tag}-${architecture}.apk" ]] || all_valid=false
        done
    fi
fi
if $all_valid; then
    log "镜像已是最新版本：$tag"
    exit 0
fi

log "开始同步 GitHub Release：$tag"
declare -A shas sizes paths
for architecture in "${ARCHITECTURES[@]}"; do
    apk_file="$UPDATES_DIR/PlateView-${tag}-${architecture}.apk"
    part_file="$UPDATES_DIR/.PlateView-${tag}-${architecture}.apk.part"
    download_url="${RELEASE_DOWNLOAD_BASE_URL%/}/${tag}/PlateView-${tag}-${architecture}.apk"
    log "开始同步 ${architecture} APK"
    curl --fail --show-error --location --continue-at - --retry 3 \
        --connect-timeout 20 --max-time 3600 --output "$part_file" "$download_url"
    [[ -s "$part_file" ]] || die "${architecture} APK为空"
    sha256=$(sha256sum "$part_file" | awk '{print $1}')
    [[ "$sha256" =~ ^[a-f0-9]{64}$ ]] || die "${architecture} APK SHA-256 无效"
    mv "$part_file" "$apk_file"
    chmod 0644 "$apk_file"
    shas[$architecture]="$sha256"
    sizes[$architecture]="$(stat -c '%s' "$apk_file")"
    paths[$architecture]="PlateView-${tag}-${architecture}.apk"
done

printf '{"versionName":"%s","releaseNotes":"","artifacts":{"arm64-v8a":{"apkPath":"%s","sha256":"%s","sizeBytes":%s},"armeabi-v7a":{"apkPath":"%s","sha256":"%s","sizeBytes":%s},"universal":{"apkPath":"%s","sha256":"%s","sizeBytes":%s}},"apkPath":"%s","sha256":"%s","sizeBytes":%s}\n' \
    "$tag" \
    "${paths[arm64-v8a]}" "${shas[arm64-v8a]}" "${sizes[arm64-v8a]}" \
    "${paths[armeabi-v7a]}" "${shas[armeabi-v7a]}" "${sizes[armeabi-v7a]}" \
    "${paths[universal]}" "${shas[universal]}" "${sizes[universal]}" \
    "${paths[universal]}" "${shas[universal]}" "${sizes[universal]}" > "${latest_file}.next"
mv "${latest_file}.next" "$latest_file"

declare -a expired_files=()
mapfile -t expired_versions < <(find "$UPDATES_DIR" -maxdepth 1 -type f -name 'PlateView-v*-*.apk' -printf '%f\n' \
    | sed -nE 's/^PlateView-(v[0-9]+\.[0-9]+\.[0-9]+)-.*$/\1/p' \
    | sort -u -V -r | tail -n +$((RETAIN_COUNT + 1)))
for expired_version in "${expired_versions[@]-}"; do
    [[ -n "$expired_version" ]] && rm -f "$UPDATES_DIR/PlateView-${expired_version}-"*.apk
done

log "镜像同步完成：$tag，三个架构 APK 已更新"
