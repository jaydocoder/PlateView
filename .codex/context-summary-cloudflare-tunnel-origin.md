## 项目上下文摘要（Cloudflare Tunnel 源服务）

生成时间：2026-08-07 17:06:00 CST

### 相似实现分析

- `compose.production.yaml`：生产 API 位于内部 `backend` 网络，Caddy 通过 `api:8080` 反向代理。
- `deploy/Caddyfile`：公网 HTTPS 请求由 Caddy 代理给 `api:8080`。
- `docs/12-部署运行手册.md`：生产编排以 Caddy 为公网入口，禁止在安全组开放 API 端口。

### 依赖与集成点

```text
Cloudflare Tunnel（服务器系统服务） -> 127.0.0.1:8080 -> Docker API -> PostgreSQL
```

Cloudflared 作为系统服务，不能解析 Docker 内部服务名 `api`。因此 API 必须只发布到回环地址，而不是公网接口。

Docker 不会为仅连接 `internal: true` 网络的容器建立可用主机端口发布规则。API 因此还需加入既有的非内部 `edge` 网络；其端口仍限制为回环地址，不会被公网访问。

### 验证策略

1. `docker compose ... config -q` 校验编排语法。
2. `docker inspect` 确认 API 的 `8080/tcp` 映射到 `127.0.0.1:8080`。
3. 服务器执行 `curl -i http://127.0.0.1:8080/health`，预期 HTTP 200。
4. 外部执行 `curl -i https://api.plateview.top/health`，预期 HTTP 200。

### 风险

- API 重建会产生短暂服务中断。
- 不得使用 `0.0.0.0:8080:8080`，否则会将 API 暴露到公网。
