# Android 开发 Skills 来源

所有目录均为当前项目本地副本，未安装到全局 Skills 目录。

| Skill | 来源 | 上游路径或安装方式 |
| --- | --- | --- |
| adaptive | `android/skills` | `jetpack-compose/adaptive` |
| styles | `android/skills` | `jetpack-compose/theming/styles` |
| navigation-3 | `android/skills` | `navigation/navigation-3` |
| testing-setup | `android/skills` | `testing/testing-setup` |
| r8-analyzer | `android/skills` | `performance/r8-analyzer` |
| perfetto-sql | `android/skills` | `profilers/perfetto-sql` |
| perfetto-trace-analysis | `android/skills` | `profilers/perfetto-trace-analysis` |
| firebase-basics | `google/skills` | `skills/cloud/firebase-basics` |
| firebase-auth-basics | `firebase/agent-skills` | 项目级 `npx skills add` 安装 |
| firebase-firestore | `firebase/agent-skills` | 项目级 `npx skills add` 安装 |
| modern-jetpack-compose | `anhvt52/jetpack-compose-skills` | `modern-jetpack-compose`，分支 `master` |
| firebase-android | 项目本地 | Firebase Android 产品路由总 Skill |
| android-development | 项目本地 | Android 开发总 Skill |

说明：`firebase/agent-skills` 当前公开集合没有独立的 Cloud Storage、Cloud Messaging 或 Analytics Skill；`firebase-android` 负责将这些需求路由至 Firebase 官方 Android SDK 文档与项目架构约束。
