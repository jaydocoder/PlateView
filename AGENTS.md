# Docker 与 Gradle 下载

- Docker/BuildKit 在构建镜像时下载 Gradle 分发包、Gradle 插件或 Maven 依赖，必须显式使用本机混合代理，不得仅依赖透明代理。
- 代理变量只允许作用于当前构建命令，禁止写入 Dockerfile、镜像层、项目配置或全局环境。构建命令使用：

```bash
HTTP_PROXY=http://127.0.0.1:7890 \
HTTPS_PROXY=http://127.0.0.1:7890 \
ALL_PROXY=socks5://127.0.0.1:7890 \
  docker build --network host \
    --build-arg HTTP_PROXY=http://127.0.0.1:7890 \
    --build-arg HTTPS_PROXY=http://127.0.0.1:7890 \
    --build-arg ALL_PROXY=socks5://127.0.0.1:7890 \
    -t <镜像名> <构建目录>
```

- 通过该方式完成镜像构建后，使用 `docker compose --env-file .env up -d --no-build` 启动服务，避免 Compose 再次触发未注入代理的构建。
