#!/usr/bin/env bash

set -euo pipefail

project_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
properties_file="$project_directory/keystore.properties"
keystore_file="$HOME/.android/plateview-release.jks"

if [[ -e "$keystore_file" || -e "$properties_file" ]]; then
    printf '%s\n' "发布签名已存在。为避免覆盖密钥或本机配置，脚本已停止。"
    exit 1
fi

mkdir -p "$HOME/.android"
umask 077

printf '%s\n' "将创建 PlateView 发布密钥。请在接下来的 keytool 提示中输入并妥善保管密码。"
keytool -genkeypair -v \
    -keystore "$keystore_file" \
    -storetype PKCS12 \
    -alias plateview \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000

while true; do
    read -r -s -p "请再次输入刚才设置的密钥库密码：" store_password
    printf '\n'
    read -r -s -p "请确认密钥库密码：" confirmed_store_password
    printf '\n'

    if [[ "$store_password" == "$confirmed_store_password" ]]; then
        break
    fi

    printf '%s\n' "两次输入不一致，请重试。"
done

temporary_properties_file="$(mktemp "$project_directory/.keystore.properties.XXXXXX")"
{
    printf 'storeFile=%s\n' "$keystore_file"
    printf 'storePassword=%s\n' "$store_password"
    printf 'keyAlias=plateview\n'
    printf 'keyPassword=%s\n' "$store_password"
} > "$temporary_properties_file"
chmod 600 "$temporary_properties_file"
mv "$temporary_properties_file" "$properties_file"
unset store_password confirmed_store_password

printf '%s\n' "发布签名配置已写入 android/keystore.properties。密钥和配置均不会提交到 Git。"
