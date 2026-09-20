# PlateView

PlateView 是面向景区入口、巡查与车辆信息核验场景的 Android 应用及自建服务端。已登录用户可通过手动输入车牌片段，快速查询车辆归属、类别与通行信息；管理员可维护车辆档案、账号、Excel 导入批次与审计记录。

当前正式版本为 `0.3.28`（`versionCode 32`）。Android 客户端最低支持 Android 12（API 31），采用 Kotlin 与 Jetpack Compose 开发；服务端采用 Kotlin、Ktor 与 PostgreSQL。

## 核心能力

- 账号密码登录，区分普通用户与管理员角色。
- 手动输入车牌，忽略大小写、空格、中点和连字符。
- 从首个有效车牌字符开始防抖模糊匹配，候选项展示真实号牌颜色、车牌与车辆类型。
- 查询村民车辆、驻景区单位车辆、驻景区企业车辆、干部车辆与喀纳斯旅游发展股份有限公司车辆详情。
- 车辆状态支持有效、严查、已拉黑、已失效与已删除；严查车辆以黄色警戒状态提示核验三证合一及车辆、人员信息。
- 保存当前账号的本机搜索历史。
- 使用 SQLCipher Room 保存加密车辆目录快照；目录未变化时，本地完成候选与详情查询，降低弱网场景的等待时间。
- 通过目录版本在登录、页面恢复前台和联网后台任务中检查数据变化；检测到变化后原子替换本地快照。
- 检测到新正式版后支持下载更新；网络中断时保留安装包断点，下次下载自动继续。
- 管理员工作台提供车辆档案、账号、Excel 导入、发布、回滚和审计查询。
- 经主管理员授权的账号可在首页按单号、车牌或微信原文查询三个工作群的车单，查看完整人员证件、来源、北京时间和关联图片；同号车单默认展示最新记录。
- Ubuntu 微信采集器在桌面登录后后台启动，按游标补传文本和图片，并在本机生成缩略图与预览图，电脑关机期间的消息会在下次开机后追赶。
- Excel 导入采用预览、行级处置、确认发布与可追溯回滚流程，支持特殊后缀车牌。新增、更新、恢复和待失效记录只要尚未确认处置，都会显示冰湖绿色提示点并排在已处理记录之前；确认或跳过后提示点消失。
- 界面使用森林冰湖绿主题和静态液态玻璃材质。为兼容不同厂商设备，未启用实时背景采样、折射或模糊渲染，也不使用边到边窗口处理。

## 工程结构

```text
PlateView/
├── android/                 Android 客户端（Compose、Hilt、Room、WorkManager）
├── server/                  Ktor REST API 与 Flyway 数据库迁移
├── collector/               基于 wx-cli 的 Ubuntu 微信车单后台采集器
├── infra/                   PostgreSQL 初始化脚本
├── deploy/                  生产部署、蓝绿切流与镜像清理脚本
├── docs/                    需求、架构、操作与部署文档
├── compose.yaml             本地开发 Docker Compose 编排
├── compose.production.yaml  生产 Docker Compose 编排
└── .github/workflows/       Android 构建与服务端自动部署工作流
```

## 角色权限

| 角色 | 功能 |
| --- | --- |
| 普通用户 | 登录、手动查询、查看车辆详情、管理本机搜索历史；经授权后可查询微信车单。 |
| 管理员 | 继承普通用户权限，并可管理车辆档案、账号、Excel 导入批次、发布回滚与审计记录。 |
| 主管理员 `admin` | 可配置用户微信车单权限并查看三个群的同步状态；自身权限不可关闭。 |

管理员界面仅是便利入口，服务端同样对管理员 API 执行角色校验。

## 本地开发

### 前置条件

- JDK 17
- Android Studio 或 Android SDK（Android 12 及以上）
- Docker 与 Docker Compose

### 启动后端

复制并填写本地环境变量文件后启动服务：

```bash
cp .env.example .env
docker compose --env-file .env up --build
```

健康检查地址：`http://127.0.0.1:8080/health`。

### 构建 Android 调试包

将 API 地址作为 Gradle 属性传入：

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  -PplateviewApiBaseUrl=http://10.0.2.2:8080/
```

调试 APK 输出路径：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

真机调试时，将地址替换为局域网开发服务器地址或开发服务器的 HTTPS 域名；不要把密码、令牌或生产数据库密码写入源码。

## 生产部署

生产服务由 PostgreSQL、Ktor API 与 Caddy 组成。API 由部署脚本管理蓝绿槽位，仅通过 Caddy 对外提供 HTTPS。

```bash
docker compose --env-file .env -f compose.production.yaml up -d
```

服务端代码推送到 `main` 后，GitHub Actions 仅在服务端、生产编排、部署脚本或部署工作流发生变化时触发。GitHub 托管运行器先执行服务端测试，再通过 Buildx 构建镜像并推送到 GHCR；服务器只拉取指定提交的不可变镜像摘要，不再运行 Gradle、Kotlin 或 Docker 镜像构建。

部署前会检查磁盘、可用内存、交换空间、一分钟负载、当前 API 与公网健康状态。只有新增或恢复数据库迁移文件时才额外执行发布前备份；每日完整备份由低优先级 systemd 定时器负责。候选 API、PostgreSQL 和 Caddy 均有 CPU、内存与进程数上限，通过 Flyway 和健康检查后才由 Caddy 平滑切流。失败时保留旧服务并保存候选容器日志。

首次部署需要配置 `DEPLOY_SSH_KEY`、`DEPLOY_KNOWN_HOSTS`、`DEPLOY_HOST`、`DEPLOY_USER` 和 `DEPLOY_PORT` Secrets。完整初始化、蓝绿切换、备份恢复、回滚和排障步骤见：[部署运行手册](docs/12-部署运行手册.md)。

## APK 获取与发布

`main` 分支每次推送会自动执行 Android 单元测试、静态检查并上传调试 APK 工件。

推送形如 `v0.3.15` 的版本标签会额外执行正式签名构建，并在 GitHub 发行版中上传 `app-release.apk`。服务器由 `root` 一次性安装 `plateview-update-mirror.timer` 后，每五分钟主动检查 GitHub Release，断点续传同一签名 APK，校验 SHA-256 后原子更新服务器镜像与 `latest.json`。客户端优先从 GitHub 下载；GitHub 不可用或中途下载失败时，会复用未完成文件并从服务器镜像继续断点下载。

当前版本：`0.3.28`（`versionCode 32`）。最新正式 APK 可在 [GitHub Releases](https://github.com/jaydocoder/PlateView/releases) 下载。

## 文档索引

- [需求规格说明书](docs/01-需求规格说明书.md)
- [交互与 UI 规范](docs/05-交互与UI规范.md)
- [数据库设计](docs/07-数据库设计.md)
- [Android 开发规范](docs/11-Android开发规范.md)
- [管理员操作手册](docs/13-管理员操作手册.md)
- [用户操作手册](docs/14-用户操作手册.md)
- [部署运行手册](docs/12-部署运行手册.md)
- [Android 加密车辆缓存与目录版本同步 ADR](docs/ADR/ADR-006-Android加密车辆缓存与目录版本同步.md)

## 验证命令

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

服务端验证命令：

```bash
cd server
./gradlew test
```
