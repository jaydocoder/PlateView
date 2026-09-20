#!/usr/bin/env bash
set -euo pipefail

source_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
install_dir="$HOME/.local/share/plateview-wechat-collector"
config_dir="$HOME/.config/plateview"
service_dir="$HOME/.config/systemd/user"

mkdir -p "$install_dir" "$config_dir" "$service_dir"
python3 -m venv "$install_dir/venv"
"$install_dir/venv/bin/pip" install --disable-pip-version-check -r "$source_dir/requirements.txt"
install -m 0755 "$source_dir/plateview_wechat_collector.py" "$install_dir/plateview_wechat_collector.py"
install -m 0644 "$source_dir/plateview-wechat-collector.service" "$service_dir/plateview-wechat-collector.service"
if [[ ! -f "$config_dir/wechat-collector.env" ]]; then
    install -m 0600 "$source_dir/wechat-collector.env.example" "$config_dir/wechat-collector.env"
    echo "已创建配置文件：$config_dir/wechat-collector.env，请填写后再启动服务。"
fi
systemctl --user daemon-reload
systemctl --user enable plateview-wechat-collector.service
echo "安装完成。配置完成后执行：systemctl --user start plateview-wechat-collector.service"
