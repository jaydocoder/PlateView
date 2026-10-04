# PlateView 网页客户端

网页客户端使用 React、TypeScript 和 Vite，生产路径为同域 `/web/`。页面只通过 Ktor API 读取业务数据，车辆、微信、附件和管理员权限仍由服务端决定。

## 本地运行

```bash
npm ci
npm run dev
```

浏览器打开 `http://127.0.0.1:5173/web/`。本地后端使用 HTTP 时，在服务端配置 `WEB_COOKIE_SECURE=false`，生产环境保持默认 `true` 并使用 HTTPS。

Vite 开发服务器会把认证、车辆、微信、日程、统计和管理请求代理到 `http://127.0.0.1:8080`；生产环境不使用该代理，直接由同域 Caddy 转发。

## 验证

```bash
npm run typecheck
npm test -- --run
npm run build
```

构建结果位于 `dist/`，部署工作流会将它上传到服务器的 `/opt/plateview/web-dist`，由 Caddy 提供静态资源。
