export type Feedback = { id: number; tone: "success" | "error"; message: string; expiresAt: number };
const key = "plateview-operation-feedback";
const listeners = new Set<(value: Feedback | null) => void>();
let current: Feedback | null = null;

export function notify(tone: Feedback["tone"], message: string) {
  current = { id: Date.now(), tone, message, expiresAt: Date.now() + (tone === "error" ? 10_000 : 5_000) };
  try { sessionStorage.setItem(key, JSON.stringify(current)); } catch { /* 存储不可用时仍显示本次提示。 */ }
  listeners.forEach(listener => listener(current));
}

export function dismissFeedback() {
  current = null;
  try { sessionStorage.removeItem(key); } catch { /* 不依赖浏览器存储关闭提示。 */ }
  listeners.forEach(listener => listener(null));
}

export function getFeedback(): Feedback | null {
  if (!current) {
    try {
      const value = JSON.parse(sessionStorage.getItem(key) || "null");
      if (value && typeof value.message === "string" && ["success", "error"].includes(value.tone) && value.expiresAt > Date.now()) current = value;
    } catch { /* 忽略过期或不可解析的提示。 */ }
  }
  return current && current.expiresAt > Date.now() ? current : null;
}

export function subscribeFeedback(listener: (value: Feedback | null) => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function operationLabel(path: string, method: string): string | null {
  const route = path.split("?")[0];
  if (method === "GET" && /^\/admin\/schedules\/templates\/\d+\/preview$/.test(route)) return "排班模板预览";
  if (method === "GET" || route === "/auth/web-refresh" || route === "/statistics/events") return null;
  if (route === "/auth/web-login") return "登录";
  if (route === "/auth/web-logout") return "退出登录";
  if (route === "/auth/profile") return "账号资料保存";
  if (route === "/auth/profile/avatar/delete") return "头像移除";
  if (route === "/auth/profile/avatar") return "头像更新";
  if (route.endsWith("/test")) return "连接测试";
  if (route.endsWith("/limits")) return "返回数量保存";
  if (route.endsWith("/api-endpoint")) return "服务地址保存";
  if (route.endsWith("/update-endpoint")) return "升级地址保存";
  if (route.startsWith("/admin/vehicles")) return route.endsWith("/status") ? "车辆状态更新" : method === "PUT" ? "车辆档案保存" : "车辆档案创建";
  if (route.startsWith("/admin/users")) return method === "PUT" ? "账号信息保存" : "账号创建";
  if (route.startsWith("/admin/imports")) return route.endsWith("/publish") ? "导入发布" : route.endsWith("/rollback") ? "导入回滚" : route.endsWith("/preview") ? "导入预览" : "导入处理";
  if (route.startsWith("/admin/schedules")) return route.endsWith("/applications") ? "排班模板应用" : "排班配置保存";
  if (route.startsWith("/admin/wechat-sync")) {
    const labels: Record<string, string> = { preview: "重构预览", lock: "维护锁定", "backup-verify": "备份校验", clean: "清理请求", verify: "重构验证", unlock: "解除维护", restore: "备份恢复请求" };
    const segments = route.split("/");
    return labels[segments[segments.length - 1]] || "微信同步操作";
  }
  return route.startsWith("/admin/") ? "操作" : null;
}
